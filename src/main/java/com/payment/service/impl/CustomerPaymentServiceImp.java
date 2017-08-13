/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.payment.service.impl;

import com.payment.bean.PaymentReceivedBean;
import com.payment.dao.CustomerDao;
import com.payment.entity.Customer;
import com.payment.entity.CustomerBillingInformation;
import com.payment.entity.CustomerPayment;
import com.payment.exceptionhandler.FileImportException;
import com.payment.parser.ExcelParser;
import com.payment.service.CustomerPaymentService;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @author mustafa
 */
@Service
public class CustomerPaymentServiceImp implements CustomerPaymentService {

    static Logger log = Logger.getLogger(CustomerPaymentServiceImp.class);
    @Autowired
    private CustomerDao userDao;

    @Override
    public List<CustomerPayment> getCustomerPayment() {
        return userDao.getCustomerPayment();
    }

    @Override
    public List<CustomerPayment> parse(MultipartFile file) {

        List<CustomerPayment> totalpayment = new ArrayList<CustomerPayment>();
        List<PaymentReceivedBean> listOfCustomerPayment = new ArrayList<PaymentReceivedBean>();

        try {
            InputStream inputStream = file.getInputStream();
            String fileName = file.getOriginalFilename();
            Workbook workbook = getWorkbook(inputStream, fileName);
            Sheet firstSheet = workbook.getSheetAt(0);

            Iterator<Row> rowIterator = firstSheet.iterator();
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (row.getRowNum() <= 0) {
                    continue;
                }
                PaymentReceivedBean paymentReceivedBean = ExcelParser.paymentProcess(row);

                if (paymentReceivedBean == null) {
                    break;
                }
                listOfCustomerPayment.add(paymentReceivedBean);
            }

            if (listOfCustomerPayment.size() != 0) {
                //payment settlement logic here
                for (PaymentReceivedBean cusBean : listOfCustomerPayment) {
                    Customer customer = userDao.getCustomerByCusID(cusBean.getCustomerId());
                    if (customer != null) {
                        CustomerBillingInformation cusBill = userDao.getCustomerBillingInformation(cusBean.getCustomerId(), cusBean.getCustomerBillingDate());

                        if (cusBill != null) {
                            // create 3 BigDecimal objects
                            BigDecimal adjustAmount;
                            MathContext mc = new MathContext(2); // 2 precision
                            adjustAmount = cusBill.getCustomerTotalBilAmount().subtract(cusBean.getPayAmount(), mc);
                            CustomerPayment customerPayment = new CustomerPayment();
                            customerPayment.setCustomerID(cusBean.getCustomerId());
                            customerPayment.setCustomerPaymentDate(cusBean.getCustomerBillingDate());
                            customerPayment.setCustomerPaymentAmount(cusBean.getPayAmount());
                            if (adjustAmount.signum() == 0) {
                                customerPayment.setCustomerPaymentprocessingstatus('Y');
                                userDao.create(customerPayment);
                                totalpayment.add(customerPayment);

                            } else if (adjustAmount.signum() > 0) {
                                customerPayment.setCustomerPaymentprocessingstatus('Y');
                                userDao.create(customerPayment);
                                totalpayment.add(customerPayment);
                            } else if (adjustAmount.signum() < 0) {
                                customerPayment.setCustomerPaymentprocessingstatus('N');
                                userDao.create(customerPayment);
                                cusBill.setCustomerRemaingBalance(adjustAmount);
                                userDao.update(cusBill);
                                totalpayment.add(customerPayment);

                            }

                        }
                    }

                }

            }

        } catch (IOException ex) {
            log.info("The specified file is not Excel file");
        } catch (FileImportException ex) {
            log.info("The specified file is not Excel file");
        }
        return totalpayment;
    }

    private Workbook getWorkbook(InputStream inputStream, String excelFileName)
            throws IOException, FileImportException {
        Workbook workbook = null;

        if (excelFileName.endsWith("xlsx")) {
            workbook = new XSSFWorkbook(inputStream);
        } else if (excelFileName.endsWith("xls")) {
            workbook = new HSSFWorkbook(inputStream);
        } else {
            log.info("The specified file is not Excel file");
            throw new FileImportException("The specified file is not Excel file");
        }

        return workbook;
    }

}
