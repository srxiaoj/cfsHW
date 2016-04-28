/*
 * The MIT License
 *
 * Copyright (c) 2015, Delta Star Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.deltastar.task7.core.service.api.impl;

import com.deltastar.task7.core.repository.api.*;
import com.deltastar.task7.core.repository.domain.*;
import com.deltastar.task7.core.service.api.EmployeeService;
import com.deltastar.task7.core.service.exception.CfsException;
import com.deltastart.task7.core.constants.CCConstants;
import com.deltastart.task7.core.constants.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Implementation of the {@link EmployeeService}.
 * <p>
 * Delta Star Team
 */
@Service
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private FundRepository fundRepository;
    @Autowired
    private PositionRepository positionRepository;
    @Autowired
    private TransitionRepository transitionRepository;
    @Autowired
    private TransitionViewRepository transitionViewRepository;
    @Autowired
    private FundPriceHistoryRepository fundPriceHistoryRepository;
    @Autowired
    private FundPriceHistoryViewRepository fundPriceHistoryViewRepository;

    /**
     * {@inheritDoc}
     */
    @Transactional
    public Employee create(final Employee employee) {
        return employeeRepository.create(employee);
    }


    /**
     * {@inheritDoc}
     */
    @Transactional
    public synchronized void updatePassword(int employeeId, String password) {

        Employee employee = employeeRepository.getEmployeeById(employeeId);
        employee.setPassword(password);
        employee.hashPassword();
        employeeRepository.update(employee);

    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    public synchronized void updatePassword(String employeeIdAsString, String password) throws CfsException {
        int employeeId = Util.formatToInteger(employeeIdAsString);
        updatePassword(employeeId, password);

    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    public synchronized void remove(final Employee employee) {
        employeeRepository.remove(employee);
    }

    @Override
    public List<Employee> getEmployeeList() {
        return employeeRepository.getEmployeeList();
    }


    private synchronized void updateLastPriceForFund(Integer fundId, long price) {
        Fund fund = fundRepository.getFundById(fundId);
        fund.setLastPrice(price);
        fundRepository.update(fund);
    }

    private synchronized void doRequestCheck(Transition transition) {

        transition.setType(CCConstants.TRAN_TYPE_REQUEST_CHECK);
        transition.setStatus(CCConstants.TRAN_STATUS_DONE);
//        transition.setExecuteDate(new Timestamp(System.currentTimeMillis()));
        transitionRepository.update(transition);


        Customer customer = customerRepository.findCustomerById(transition.getCustomerId());
        customer.setCashToBeChecked(customer.getCashToBeChecked() - transition.getAmount());
        customerRepository.update(customer);
    }

    private synchronized void doBuyFund(Transition transition, long price) {
        long shares = Util.getShares(transition.getAmount(), price);
        //FIXME overflow decimal three digit.
        Position possessedPosition = positionRepository.getPossessedPositionByCustomerIdAndFundId(transition.getCustomerId(), transition.getFundId());
        if (possessedPosition == null) {
            possessedPosition = positionRepository.getPositionById(transition.getPositionId());
            possessedPosition.setStatus(CCConstants.POSITION_STATUS_IN_POSSESSION);
        } else {
            Position pendingPosition = positionRepository.getPositionById(transition.getPositionId());
            positionRepository.remove(pendingPosition);
        }

        possessedPosition.setShares(possessedPosition.getShares() + shares);
        positionRepository.update(possessedPosition);
        transition.setPrice(price);
        transition.setStatus(CCConstants.TRAN_STATUS_DONE);
//        transition.setExecuteDate(new Timestamp(System.currentTimeMillis()));
        transitionRepository.update(transition);

    }

    private synchronized void doSellFund(Transition transition, long price) {

        long cash = Util.formatCash(price, transition.getShares());


        Customer customer = customerRepository.findCustomerById(transition.getCustomerId());
        customer.setCash(customer.getCash() + cash);
        customerRepository.update(customer);


        Position pendingPosition = positionRepository.getPositionById(transition.getPositionId());
        pendingPosition.setStatus(CCConstants.POSITION_STATUS_SOLD);
        positionRepository.update(pendingPosition);
        transition.setPrice(price);
        transition.setAmount(cash);
        transition.setStatus(CCConstants.TRAN_STATUS_DONE);
//        transition.setExecuteDate(new Timestamp(System.currentTimeMillis()));
        transitionRepository.update(transition);

    }

    private synchronized void doDeposit(Transition transition) {

        Customer customer = customerRepository.findCustomerById(transition.getCustomerId());
        customer.setCash(customer.getCash() + transition.getAmount());
        customer.setCashToBeDeposited(customer.getCashToBeDeposited() - transition.getAmount());
        customerRepository.update(customer);


        transition.setStatus(CCConstants.TRAN_STATUS_DONE);
//        transition.setExecuteDate(new Timestamp(System.currentTimeMillis()));
        transitionRepository.update(transition);

    }

    @Override
    @Transactional
    public synchronized void depositCheckByCustomerId(String customerIdAsString, String amountAsString) throws CfsException {

        int customerId = Util.formatToInteger(customerIdAsString);
        Customer customer = customerRepository.findCustomerById(customerId);
        if (customer == null) {
            throw new CfsException(CfsException.CODE_INVALID_CUSTOMER);
        }
        depositCheck(customer, amountAsString);

    }

    @Override
    @Transactional
    public synchronized void depositCheckByCustomerName(String customerName, String amountAsString) throws CfsException {

        Customer customer = customerRepository.getCustomerByUserName(customerName);
        if (customer == null) {
            throw new CfsException(CfsException.CODE_INVALID_CUSTOMER);
        }
        depositCheck(customer, amountAsString);

    }


    private synchronized void depositCheck(Customer customer, String amountAsString) throws CfsException {
        long amount = Util.formatToLong(amountAsString);

        if (!Util.isValidTransactionAmount(amount)) {
            throw new CfsException(CfsException.CODE_MAX_DEPOSITION);
        }
        Transition transition = new Transition();
        transition.setAmount(amount);
        transition.setCustomerId(customer.getId());
        transition.setType(CCConstants.TRAN_TYPE_DEPOSIT_CHECK);
        transition.setStatus(CCConstants.TRAN_STATUS_PENDING);
        transitionRepository.create(transition);

        customer.setCashToBeDeposited(customer.getCashToBeDeposited() + amount);
        customerRepository.update(customer);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    public Employee getEmployeeByUserName(final String userName) {
        return employeeRepository.getEmployeeByUserName(userName);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    public boolean login(final String email, final String password) {

        Employee employee = employeeRepository.getEmployeeByUserName(email);
        if (employee == null) {
            return false;
        } else if (CCConstants.EMPLOYEE_TYPE_SUPER_ADMIN == employee.getType()) {
            return password.equals(employee.getPassword());
        } else {
            return employee.checkPassword(password);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    public Employee loginEmployee(final String email, final String password) {

        Employee employee = employeeRepository.getEmployeeByUserName(email);
        if (employee == null) {
            return null;
        } else if (CCConstants.EMPLOYEE_TYPE_SUPER_ADMIN == employee.getType()) {
            return password.equals(employee.getPassword()) ? employee : null;
        } else {
            return employee.checkPassword(password) ? employee : null;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    public Employee getEmployeeById(final String idAsString) throws CfsException {
        int id = Util.formatToInteger(idAsString);
        return employeeRepository.getEmployeeById(id);

    }

    @Override
    public synchronized void createCustomer(String userName, String password,
                                            String firstName, String lastName,
                                            String addressLine1, String addressLine2, String city,
                                            String state, String zipcode) throws CfsException {
        validationParameterCustomer(userName, password, firstName, lastName, addressLine1,
                addressLine2, city, state, zipcode);
        Customer customer = validationExtraBusinessRestraintsCustomer(userName);
        if (customer == null) {
            customer = new Customer();
            customer.setUserName(userName.trim());
            customer.setPassword(password);
            customer.setFirstName(firstName);
            customer.setLastName(lastName);
            customer.setAddressLine1(addressLine1);
            customer.setAddressLine2(addressLine2);
            customer.setCity(city);
            customer.setState(state);
            customer.setZipcode(zipcode);
            customerRepository.create(customer);
        } else {
            customer.setUserName(userName.trim());
            customer.setPassword(password);
            customer.hashPassword();
            customer.setFirstName(firstName);
            customer.setLastName(lastName);
            customer.setAddressLine1(addressLine1);
            customer.setAddressLine2(addressLine2);
            customer.setCity(city);
            customer.setState(state);
            customer.setZipcode(zipcode);
            customerRepository.update(customer);
        }


    }

    @Override
    public synchronized void createEmployee(String userName, String password, String firstName, String lastName) throws CfsException {

        createEmployee(userName, password, firstName, lastName, null, null, null, null, null);
    }

    @Override
    public synchronized void createEmployee(String userName, String password, String firstName, String lastName, String addressLine1, String addressLine2, String city, String state, String zip) throws CfsException {
        validationParameterEmployee(userName, password, firstName, lastName, addressLine1, addressLine2, city, state, zip);
        validationExtraBusinessRestraintsEmployee(userName);
        Employee employee = new Employee(userName.trim(), password, firstName.trim(), lastName.trim());
        employeeRepository.create(employee);
    }

    @Override
    public List<Customer> getCustomerList() {
        return customerRepository.findAllCustomer();
    }

    @Override
    public synchronized void updateCustomerPassword(String customerIdAsString, String password) throws CfsException {
        int customerId = Util.formatToInteger(customerIdAsString);
        Customer customer = customerRepository.findCustomerById(customerId);
        validationParameterEmployeeUpdateCustomerPassword(password);
        customer.setPassword(password);
        customer.hashPassword();
        customerRepository.update(customer);
    }

    @Override
    public synchronized void updateCustomerProfile(String customerIdAsString, String userName, String firstName, String lastName, String addressLine1, String addressLine2, String city, String state, String zipcode) throws CfsException {

        int customerId = Util.formatToInteger(customerIdAsString);
        Customer customer = customerRepository.findCustomerById(customerId);
        validationParameterCustomer(customer);
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setAddressLine1(addressLine1);
        customer.setAddressLine2(addressLine2);
        customer.setCity(city);
        customer.setState(state);
        customer.setZipcode(zipcode);
        customerRepository.update(customer);
    }

    @Override
    public Customer getCustomerById(String customerIdAsString) throws CfsException {

        int customerId = Util.formatToInteger(customerIdAsString);

        Customer result = customerRepository.findCustomerById(customerId);

        if (result == null) {
            throw new CfsException(CfsException.CODE_INVALID_CUSTOMER_ID);
        }

        return result;

    }

    @Override
    public List<TransitionView> getTransitionViewList() {
        return transitionViewRepository.getTransitionList();
    }

    @Override
    public List<TransitionView> getTransitionViewList(String customerIdAsString) throws CfsException {
        int customerId = Util.formatToInteger(customerIdAsString);
        return transitionViewRepository.getTransitionListByCustomerId(customerId);
    }

    @Override
    public List<Fund> getFundList() {
        return fundRepository.findAllFund();
    }

    public Fund getFundById(String fundIdAsString) throws CfsException {

        int fundId = Util.formatToInteger(fundIdAsString);
        Fund result = fundRepository.getFundById(fundId);

        if (result == null) {
            throw new CfsException(CfsException.CODE_INVALID_FUND_ID);
        }

        return result;
    }

    @Override
    public List<FundPriceHistoryView> getFundPriceHistoryViewList(String fundIdAsString) throws CfsException {
        int fundId = Util.formatToInteger(fundIdAsString);
        return fundPriceHistoryViewRepository.getFundPriceHistoryViewListById(fundId);
    }

    @Override
    public List<List<?>> search(String keywords) throws CfsException {

        if (keywords == null || keywords.replaceAll(" ", "").equals("")) {
            throw new CfsException(CfsException.CODE_INVALID_KEYWORDS);
        }
        List<List<?>> result = new ArrayList<>();
        List<Customer> customerList = customerRepository.findCustomerByUserNameOrFirstNameOrLastName(keywords, keywords, keywords);
        List<Fund> fundList = fundRepository.findFundByFundNameOrSymbol(keywords, keywords);

        result.add(customerList);
        result.add(fundList);

        return result;
    }

    @Transactional
    @Override
    public synchronized void executeTransitionDay(String[] priceArray, String[] fundIdArray, String executionDay) throws CfsException {
        executeTransitionDay(priceArray, fundIdArray, executionDay, false);
    }

    @Transactional
    @Override
    public synchronized void executeTransitionDay(String[] priceArray, String[] fundIdArray, String executionDay, boolean autoIncrementTransitionDay) throws CfsException {

        List<Transition> pendingTransitionList = transitionRepository.getPendingTransitionList();


        //deposit first
        Timestamp currentDayTimestamp = Util.getCurrentDayTimestamp();
        for (Transition transition : pendingTransitionList) {
            transition.setExecuteDate(currentDayTimestamp);
            switch (transition.getType()) {
                case CCConstants.TRAN_TYPE_DEPOSIT_CHECK:
                    doDeposit(transition);
                    break;
                default:
                    break;
            }
        }


        if (priceArray != null && fundIdArray != null) {

            for (int i = 0; i < priceArray.length; i++) {

                if (!Util.isEmpty(priceArray[i])) {

                    long price = Util.formatToLong(priceArray[i]);

                    if (!Util.isValidTransactionAmount(price)) {
                        throw new CfsException(CfsException.CODE_MAX_DEPOSITION);
                    }

                    int fundId = Util.formatToInteger(fundIdArray[i]);
                    Timestamp timestamp;
                    Fund fund = fundRepository.getFundById(fundId);


                    if (autoIncrementTransitionDay) {
                        timestamp = Util.incrementTwoDay(fund.getLastTransitionDay());
                    } else {
                        timestamp = Util.formatTimeStamp(executionDay);
                        if (fund.getLastTransitionDay() != null && !fund.getLastTransitionDay().before(timestamp)) {
                            throw new CfsException(CfsException.CODE_INVALID_EXECUTION_DATE,
                                    MessageFormat.format(ResourceBundle.getBundle("cfs").getString("invalid.execution.date"),
                                            Util.formatTime(fund.getLastTransitionDay())));
                        }
                    }
                    fund.setLastTransitionDay(timestamp);
                    fundRepository.update(fund);

                    FundPriceHistory fundPriceHistory = new FundPriceHistory();
                    fundPriceHistory.setFundId(fundId);
                    fundPriceHistory.setPrice(price);
                    fundPriceHistory.setPriceDate(timestamp);
                    fundPriceHistoryRepository.create(fundPriceHistory);
                    updateLastPriceForFund(fundId, price);


                    for (Transition transition : pendingTransitionList) {
                        transition.setExecuteDate(timestamp);
                        switch (transition.getType()) {
                            case CCConstants.TRAN_TYPE_SELL_FUND:
                                if (fundId == transition.getFundId()) {
                                    doSellFund(transition, price);
                                }
                                break;
                            case CCConstants.TRAN_TYPE_BUY_FUND:
                                if (fundId == transition.getFundId()) {
                                    doBuyFund(transition, price);
                                }
                                break;
                            default:
                                break;
                        }
                    }
                } else {
                    System.out.println("fundList :" + fundIdArray[i]);
                }
            }

        }


        //request check last
        for (Transition transition : pendingTransitionList) {
            transition.setExecuteDate(currentDayTimestamp);
            switch (transition.getType()) {
                case CCConstants.TRAN_TYPE_REQUEST_CHECK:
                    doRequestCheck(transition);
                    break;
                default:
                    break;
            }
        }

    }


    @Override
    public synchronized void createFundExample(String fundName, String symbol, String initialValueAsString, String comment) throws CfsException {
        validationParameter(fundName, symbol, initialValueAsString, comment);
        Fund fund = validateExtraBusinessRestraint(fundName, symbol);
        long initialPrice = Util.formatToLong(initialValueAsString);
        if (!Util.isValidInitialPrice(initialPrice)) {
            throw new CfsException(CfsException.CODE_INVALID_INITIAL_PRICE);
        }

        if (fund == null) {
            fund = new Fund(fundName.trim(), symbol.trim(), initialPrice, comment != null ? comment.trim() : "");
            fund.setLastPrice(initialPrice);//set last price as initial price for the first time.
            try {
                fundRepository.create(fund);
                //init the initial price into history.
                FundPriceHistory fundPriceHistory = new FundPriceHistory();
                fundPriceHistory.setFundId(fund.getId());
                fundPriceHistory.setPrice(fund.getLastPrice());
                fundPriceHistory.setPriceDate(Util.getCurrentDayTimestamp());
                fundPriceHistoryRepository.create(fundPriceHistory);

            } catch (Exception e) {
                e.printStackTrace();
                throw new CfsException(e.getMessage());
            }
        }else {
            fund.setFundName(fundName.trim());
            fund.setSymbol(symbol.trim());
            fund.setInitialPrice(initialPrice);
            fund.setComment(comment != null ? comment.trim() : "");
            fund.setLastPrice(initialPrice);

            try {
                fundRepository.update(fund);
                //init the initial price into history.
                FundPriceHistory fundPriceHistory = new FundPriceHistory();
                fundPriceHistory.setFundId(fund.getId());
                fundPriceHistory.setPrice(fund.getLastPrice());
                fundPriceHistory.setPriceDate(Util.getCurrentDayTimestamp());
                fundPriceHistoryRepository.create(fundPriceHistory);

            } catch (Exception e) {
                e.printStackTrace();
                throw new CfsException(e.getMessage());
            }
        }


    }

    /**
     * Check whether there is any duplicated fund name or symbol or not. If there is, throw exceptioin.
     * This extra validation is done based on our own logic. For example, if you are withdrawing some money,
     * you should check the balance. If you are creating customer, you should check the existence of user name.
     * In any case, anything that may produce an error should be pre-checked in this step.
     *
     * @throws CfsException
     */
    private Fund validateExtraBusinessRestraint(String fundName, String symbol) throws CfsException {


        Fund fundBySymbol = fundRepository.getFundBySymbol(symbol);
        if (fundBySymbol != null) {
            return fundBySymbol;
        }

        Fund name = fundRepository.getFundByName(fundName);
        if (name != null) {
            return name;
        }
        return null;
    }

    /**
     * check the fund name and symbol is null or not first. If it is null, throw exception.
     * Every method in service should check the validation of the parameter as user's input is evil.
     *
     * @throws CfsException
     */
    private void validationParameter(String fundName, String symbol, String initialValueAsString, String comment) throws CfsException {
        if (fundName == null || fundName.replaceAll(" ", "").equals("")) {
            throw new CfsException(CfsException.CODE_INVALID_FUND_NAME);
        }

        if (symbol == null || symbol.replaceAll(" ", "").equals("")) {
            throw new CfsException(CfsException.CODE_INVALID_SYMBOL);
        }

        try {
            double res = Double.parseDouble(initialValueAsString);
        } catch (Exception e) {
            throw new CfsException(CfsException.CODE_INVALID_INITIAL_PRICE);
        }
        Util.validScript(fundName);
        Util.validScript(symbol);
        Util.validScript(comment);
    }

    private void validationParameter2(String fundName, String symbol) throws CfsException {
        if (fundName == null || fundName.replaceAll(" ", "").equals("")) {
            throw new CfsException(CfsException.CODE_INVALID_FUND_NAME);
        }

        if (symbol == null || symbol.replaceAll(" ", "").equals("")) {
            throw new CfsException(CfsException.CODE_INVALID_SYMBOL);
        }
        Util.validScript(fundName);
        Util.validScript(symbol);
    }


    private void validationParameterEmployee(String userName, String password, String firstName, String lastName, String addressLine1, String addressLine2, String city, String state, String zip) throws CfsException {
        if (userName == null || userName.replaceAll("  ", " ").trim().length() == 0) {
            throw new CfsException(CfsException.CODE_INVALID_EMPLOYEE_USERNAME);
        }
        if (password == null || password.length() < 6) {
            throw new CfsException(CfsException.CODE_INVALID_EMPLOYEE_PASSWORD);
        }
        if (firstName == null || firstName.replaceAll("  ", " ").trim().length() == 0) {
            throw new CfsException(CfsException.CODE_INVALID_EMPLOYEE_FIRST_NAME);
        }
        if (lastName == null || lastName.replaceAll("  ", " ").trim().length() == 0) {
            throw new CfsException(CfsException.CODE_INVALID_EMPLOYEE_LAST_NAME);
        }
        Util.validScript(userName);
        Util.validScript(password);
        Util.validScript(firstName);
        Util.validScript(lastName);

        Util.validScript(addressLine1);
        Util.validScript(addressLine2);
        Util.validScript(city);
        Util.validScript(state);
        Util.validScript(zip);


    }

    private void validationParameterEmployeeUpdateCustomerPassword(String newPassword) throws CfsException {
        if (newPassword == null || newPassword.length() < 6) {
            throw new CfsException(CfsException.CODE_INVALID_EMPLOYEE_UPDATE_CUSTOMER_PASSWORD);
        }
    }

    private void validationParameterCustomer(String userName, String password, String firstName, String lastName, String addressLine1, String addressLine2, String city, String state, String zipcode) throws CfsException {
        if (userName == null || userName.length() == 0) {
            throw new CfsException(CfsException.CODE_INVALID_CUSTOMER_USERNAME);
        }

        if (password == null || password.length() < 6) {
            throw new CfsException(CfsException.CODE_INVALID_CUSTOMER_PASSWORD);
        }

        if (firstName == null || firstName.length() == 0) {
            throw new CfsException(CfsException.CODE_INVALID_CUSTOMER_FIRST_NAME);
        }
        if (lastName == null || lastName.length() == 0) {
            throw new CfsException(CfsException.CODE_INVALID_CUSTOMER_LAST_NAME);
        }
        if (addressLine1 == null || addressLine1.length() == 0) {
            throw new CfsException(CfsException.CODE_INVALID_CUSTOMER_ADDRESS_LINE1);
        }
        if (city == null || city.length() == 0) {
            throw new CfsException(CfsException.CODE_INVALID_CUSTOMER_CITY);
        }
        if (state == null || state.length() == 0) {
            throw new CfsException(CfsException.CODE_INVALID_CUSTOMER_STATE);
        }
        if (zipcode == null || zipcode.length() == 0) {
            throw new CfsException(CfsException.CODE_INVALID_CUSTOMER_ZIPCODE);
        }
        Util.validScript(userName);
        Util.validScript(password);
        Util.validScript(firstName);
        Util.validScript(lastName);
        Util.validScript(addressLine1);
        Util.validScript(addressLine2);
        Util.validScript(city);
        Util.validScript(state);
        Util.validScript(zipcode);
    }


    private void validationParameterCustomer(Customer customer) throws CfsException {
        validationParameterCustomer(customer.getUserName(), customer.getPassword(),
                customer.getFirstName(), customer.getLastName(),
                customer.getAddressLine1(), customer.getAddressLine2(),
                customer.getCity(), customer.getState(), customer.getZipcode());
    }

    private Customer validationExtraBusinessRestraintsCustomer(String userName) {
        return customerRepository.getCustomerByUserName(userName);
    }

    private void validationExtraBusinessRestraintsEmployee(String userName) throws CfsException {
        if (employeeRepository.getEmployeeByUserName(userName) != null) {
            throw new CfsException(CfsException.CODE_INVALID_EMPLOYEE);
        }
    }

    @Override
    public Employee deleteEmployeeById(String idAsString) throws CfsException {
        Employee employee = getEmployeeById(idAsString);
        employeeRepository.remove(employee);
        return employee;
    }
}
