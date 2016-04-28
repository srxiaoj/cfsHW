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
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.deltastar.task7.core.service.api.impl;

import com.deltastar.task7.core.repository.api.*;
import com.deltastar.task7.core.repository.domain.*;
import com.deltastar.task7.core.service.api.CustomerService;
import com.deltastar.task7.core.service.exception.CfsException;
import com.deltastart.task7.core.constants.CCConstants;
import com.deltastart.task7.core.constants.Util;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of the {@link CustomerService}.
 * <p>
 * Delta Star Team
 */
@Service
@Transactional
public class CustomerServiceImpl implements CustomerService {

    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private FundRepository fundRepository;
    @Autowired
    private FundPriceHistoryViewRepository fundPriceHistoryViewRepository;
    @Autowired
    private PositionViewRepository positionViewRepository;
    @Autowired
    private PositionRepository positionRepository;
    @Autowired
    private TransitionRepository transitionRepository;
    @Autowired
    private TransitionViewRepository transitionViewRepository;

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    public Customer getCustomerByUserName(final String userName) {
        return customerRepository.getCustomerByUserName(userName);
    }
    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    public boolean login(final String userName, final String password) {
        Customer customer = customerRepository.getCustomerByUserName(userName);
        return customer != null && customer.checkPassword(password);
    }
    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    public Customer loginCustomer(final String userName, final String password) {
        Customer customer = customerRepository.getCustomerByUserName(userName);
        boolean result = customer != null && customer.checkPassword(password);
        if (result) {
            return customer;
        } else {
            return null;
        }
    }
    @Transactional(readOnly = true)
    public List<Customer> getCustomerList() {
        return customerRepository.findAllCustomer();
    }
    @Transactional(readOnly = true)
    @Override
    public Customer getCustomerById(String customerId) throws CfsException {
        Integer id = Integer.valueOf(customerId);
        return getCustomerById(id);
    }//why there is String customer Id
    @Transactional(readOnly = true)
    @Override
    public Customer getCustomerById(int customerId) throws CfsException {
        return customerRepository.findCustomerById(customerId);
    }
    @Override
    @Transactional
    public synchronized void buyFund(String customerIdAsString, String fundIdAsString, String amountAsString) throws CfsException {
        Customer customer = customerRepository.findCustomerById(Util.formatToInteger(customerIdAsString));
        int fundId;
        long amount;
        try {
            fundId = Integer.valueOf(fundIdAsString);
            amount = Util.formatToLong(amountAsString);
        } catch (Exception e) {
            throw new CfsException(CfsException.CODE_INVALID_INPUT_DATA);
        }
        if (!Util.isValidTransactionAmount(amount)) {
            throw new CfsException(CfsException.CODE_MAX_DEPOSITION);
        }
        if (customer.getCash() < amount) {
            throw new CfsException(CfsException.CODE_INSUFFICIENT_BALANCE);
        }
        customer.setCash(customer.getCash() - amount);
        customerRepository.update(customer);
        Fund fund = fundRepository.getFundById(fundId);
        if (fund == null) {
            throw new CfsException(CfsException.CODE_INVALID_FUND_NAME);
        }
        Position position = new Position(fund.getId(), customer.getId(), CCConstants.POSITION_STATUS_TO_BE_BOUGHT);
        positionRepository.create(position);
        Transition transition = new Transition();
        transition.setPositionId(position.getId());
        transition.setAmount(amount);
        transition.setCustomerId(customer.getId());
        transition.setFundId(fund.getId());
        transition.setType(CCConstants.TRAN_TYPE_BUY_FUND);
        transition.setStatus(CCConstants.TRAN_STATUS_PENDING);
        transitionRepository.create(transition);
    }

    @Transactional
    public synchronized void buyFundBySymbolForTask8(String customerIdAsString, String fundSymbol, String cashValue) throws CfsException {
        System.out.println("Begin Thread :" + Thread.currentThread().getName() + ", buyFundBySymbolForTask8 : ");
        Customer customer = customerRepository.findCustomerById(Util.formatToInteger(customerIdAsString));
        long amount;
        try {
            Util.validScript(fundSymbol);
            amount = Util.formatToLong(cashValue);
        } catch (Exception e) {
            throw new CfsException(CfsException.CODE_INVALID_INPUT_DATA);
        }
        if (!Util.isValidTransactionAmount(amount)) {
            throw new CfsException(CfsException.CODE_MAX_DEPOSITION);
        }
        if (customer.getCash() < amount) {
            throw new CfsException(CfsException.CODE_INSUFFICIENT_BALANCE);
        }
        Fund fund = fundRepository.getFundBySymbol(fundSymbol);
        if (fund == null) {
            throw new CfsException(CfsException.CODE_INVALID_SYMBOL);
        }
        long pricePerShare = fund.getLastPrice();
//        if (amount < pricePerShare) {
//            throw new CfsException(CfsException.CODE_INVALID_MINI_SHARES_AMOUNT);
//        }
        long shares = amount / pricePerShare;
        amount = pricePerShare * shares;
        customer.setCash(customer.getCash() - amount);
        customerRepository.update(customer);

//        try{
//            customerRepository.customerGetCash(Util.formatToInteger(customerIdAsString),amount);
//        } catch(Exception e){
//            e.getMessage();
//        }
//
        boolean shouldSavePositionId = false;
        Position position = positionRepository.getPossessedPositionByCustomerIdAndFundId(customer.getId(), fund.getId());
        if (position == null) {
            position = new Position(fund.getId(), customer.getId(), CCConstants.POSITION_STATUS_IN_POSSESSION);
            position.setShares(Util.regulateShares(shares));
            positionRepository.create(position);
            shouldSavePositionId = true;
        } else {
            position.setShares(position.getShares() + Util.regulateShares(shares));
            positionRepository.update(position);
        }

        //merge position list if necessary
        List<Position> inPossessionPositionList = positionRepository.getPossessedPositionByCustomerIdAndFundIdAndStatus(customer.getId(), fund.getId());
        if (inPossessionPositionList != null && inPossessionPositionList.size() > 1) {
            long totalShares = inPossessionPositionList.get(0).getShares();

            for (int i = 1; i < inPossessionPositionList.size(); i++) {
                totalShares += inPossessionPositionList.get(i).getShares();
                positionRepository.remove(inPossessionPositionList.get(i));
            }
            inPossessionPositionList.get(0).setShares(totalShares);
            positionRepository.update(inPossessionPositionList.get(0));
        }
        Transition transition = new Transition();
        if (shouldSavePositionId) {
            transition.setPositionId(position.getId());
        }
        transition.setAmount(amount);
        transition.setShares(Util.regulateShares(shares));
        transition.setPrice(pricePerShare);
        transition.setCustomerId(customer.getId());
        transition.setFundId(fund.getId());
        transition.setType(CCConstants.TRAN_TYPE_BUY_FUND);
        transition.setStatus(CCConstants.TRAN_STATUS_DONE);
        transition.setExecuteDate(Util.getCurrentDayTimestamp());
        transitionRepository.create(transition);
        System.out.println("End Thread :" + Thread.currentThread().getName() + ", buyFundBySymbolForTask8 : ");
    }

    @Override
    @Transactional
    public synchronized void sellFund(String customerIdAsString, String fundIdAsString, String sharesAsString) throws CfsException {
        Customer customer = customerRepository.findCustomerById(Util.formatToInteger(customerIdAsString));
        int fundId;
        long shares;
        try {
            fundId = Integer.valueOf(fundIdAsString);
            shares = Util.formatToLong(sharesAsString);
        } catch (Exception e) {
            throw new CfsException(CfsException.CODE_INVALID_INPUT_DATA);
        }
        if (!Util.isValidTransactionAmount(shares)) {
            throw new CfsException(CfsException.CODE_MAX_DEPOSITION);
        }
        Fund fund = fundRepository.getFundById(fundId);
        if (fund == null) {
            throw new CfsException(CfsException.CODE_INVALID_FUND_ID);
        }
        sellFund(customer, fund, shares);
    }

    @Transactional
    private synchronized void sellFund(Customer customer, Fund fund, long shares) throws CfsException {
        Position position = positionRepository.getPossessedPositionByCustomerIdAndFundId(customer.getId(), fund.getId());
        if (position == null || position.getShares() < shares) {
            throw new CfsException(CfsException.CODE_INSUFFICIENT_SHARES);
        }
        //update the current position.
        position.setShares(position.getShares() - shares);
        positionRepository.update(position);
        //create the pending position.
        Position pendingPosition = new Position(fund.getId(), customer.getId(), CCConstants.POSITION_STATUS_TO_BE_SOLD);
        pendingPosition.setShares(shares);
        positionRepository.create(pendingPosition);
        //create the pending transition.
        Transition transition = new Transition();
        transition.setShares(shares);
        transition.setCustomerId(customer.getId());
        transition.setFundId(fund.getId());
        transition.setPositionId(pendingPosition.getId());
        transition.setType(CCConstants.TRAN_TYPE_SELL_FUND);
        transition.setStatus(CCConstants.TRAN_STATUS_PENDING);
        transitionRepository.create(transition);
    }

    @Transactional
    private synchronized void sellFundForTask8(Customer customer, Fund fund, long shares) throws CfsException {
        Position position = positionRepository.getPossessedPositionByCustomerIdAndFundId(customer.getId(), fund.getId());
        if (position == null || position.getShares() < shares) {
            throw new CfsException(CfsException.CODE_INSUFFICIENT_SHARES);
        }
        //update the current position.
        position.setShares(position.getShares() - shares);
        positionRepository.update(position);
        //update the balance
        long cash = Util.formatCash(fund.getLastPrice(), shares);
        customer.setCash(customer.getCash() + cash);
        customerRepository.update(customer);
        //create the pending transition.
        Transition transition = new Transition();
        transition.setShares(shares);
        //set value for price and amount
        transition.setPrice(fund.getLastPrice());
        transition.setAmount(Util.formatCash(shares, fund.getLastPrice()));
        transition.setCustomerId(customer.getId());
        transition.setFundId(fund.getId());
        //remove the position id to avoid showing duplicated data
//        transition.setPositionId(position.getId());
        transition.setType(CCConstants.TRAN_TYPE_SELL_FUND);
        transition.setStatus(CCConstants.TRAN_STATUS_DONE);
        //fix issues that the transition shows "To be executed" even after transition day.
        transition.setExecuteDate(Util.getCurrentDayTimestamp());
        transitionRepository.create(transition);
    }
    @Transactional
    @Override
    public synchronized void sellFundBySymbolForTask8(String customerIdAsString, String fundSymbol, String numShares) throws CfsException {
        Customer customer = customerRepository.findCustomerById(Util.formatToInteger(customerIdAsString));

        long shares;
        try {
            Util.validScript(fundSymbol);
            shares = Util.formatToLong(numShares);
            long left = shares % 1000;
            if (left != 0) {
                throw new CfsException(CfsException.CODE_INVALID_SHARES);
            }
        } catch (Exception e) {
            throw new CfsException(CfsException.CODE_INVALID_INPUT_DATA);
        }

        if (!Util.isValidTransactionAmount(shares)) {
            throw new CfsException(CfsException.CODE_MAX_DEPOSITION);
        }

        Fund fund = fundRepository.getFundBySymbol(fundSymbol);
        if (fund == null) {
            throw new CfsException(CfsException.CODE_INVALID_SYMBOL);
        }
        sellFundForTask8(customer, fund, shares);


    }

    @Override
    @Transactional
    public synchronized void requestCheck(String customerIdString, String amountAsString) throws CfsException {

        long amount;
        Customer customer = customerRepository.findCustomerById(Util.formatToInteger(customerIdString));
        try {
            amount = Util.formatToLong(amountAsString);
        } catch (Exception e) {
            throw new CfsException(CfsException.CODE_INVALID_INPUT_DATA);
        }

        if (!Util.isValidTransactionAmount(amount)) {
            throw new CfsException(CfsException.CODE_MAX_DEPOSITION);
        }
        if (customer.getCash() < amount) {
            throw new CfsException(CfsException.CODE_INSUFFICIENT_BALANCE);
        }
        //update the cash first to avoid request the check two times.
        customer.setCashToBeChecked(customer.getCashToBeChecked() + amount);
        customer.setCash(customer.getCash() - amount);
        customerRepository.update(customer);

        Transition transition = new Transition();
        transition.setAmount(amount);
        transition.setCustomerId(customer.getId());
        transition.setType(CCConstants.TRAN_TYPE_REQUEST_CHECK);
        transition.setStatus(CCConstants.TRAN_STATUS_PENDING);
        transitionRepository.create(transition);
    }
    @Override
    public List<TransitionView> getTransitionViewListByCustomerId(String customerIdAsString) throws CfsException {
        int customerId = Util.formatToInteger(customerIdAsString);
        return getTransitionViewListByCustomerId(customerId);
    }
    @Override
    public List<TransitionView> getTransitionViewListByCustomerId(int customerId) throws CfsException {
        return transitionViewRepository.getTransitionListByCustomerId(customerId);
    }
    @Override
    public List<PositionView> getPositionViewListByCustomerIdAndStatus(String customerIdAsString, String positionStatusAsString) throws CfsException {
        int customerId = Util.formatToInteger(customerIdAsString);
        byte positionStatus = Util.formatToByte(positionStatusAsString);
        return getPositionViewListByCustomerIdAndStatus(customerId, positionStatus);
    }

    @Override
    public List<PositionView> getPositionViewListByCustomerIdAndStatus(int customerId, byte positionStatus) throws CfsException {
        return positionViewRepository.getPositionViewListByCustomerId(customerId, positionStatus);
    }

    @Override
    public List<Fund> getFundList() {
        return fundRepository.findAllFund();
    }

    @Override
    public Fund getFundById(String fundIdAsString) throws CfsException {
        int fundId = Util.formatToInteger(fundIdAsString);
        Fund fund = fundRepository.getFundById(fundId);
        if (fund == null) {
            throw new CfsException(CfsException.CODE_INVALID_FUND_ID);
        }
        return fund;
    }

    @Override
    public List<Fund> search(String keywords) throws CfsException {
        if (keywords == null || keywords.replaceAll(" ", "").equals("")) {
            throw new CfsException(CfsException.CODE_INVALID_KEYWORDS);
        }
        return fundRepository.findFundByFundNameOrSymbol(keywords, keywords);
    }

    @Override
    public synchronized void updatePassword(int customerId, String newPassword) {

        Customer customer = customerRepository.findCustomerById(customerId);
        customer.setPassword(newPassword);
        customer.hashPassword();
        customerRepository.update(customer);
    }

    @Override
    public synchronized void updatePassword(String customerIdAsPassword, String newPassword) throws CfsException {
        int employeeId = Util.formatToInteger(customerIdAsPassword);
        updatePassword(employeeId, newPassword);
    }


    @Override
    public String generateBarChartData(String fundIdAsString) throws CfsException {
        int fundId = Util.formatToInteger(fundIdAsString);
        List<FundPriceHistoryView> fundPriceHistoryViewList = fundPriceHistoryViewRepository.getFundPriceHistoryViewListById(fundId);

        if (Util.isEmptyList(fundPriceHistoryViewList)) {
            return null;
        }
        String[] labelArray = new String[fundPriceHistoryViewList.size()];
        for (int i = 0; i < fundPriceHistoryViewList.size(); i++) {
            labelArray[i] = Util.formatTime(fundPriceHistoryViewList.get(i).getPriceDate());
        }


        DataSet[] dataSetArray = new DataSet[1];
        dataSetArray[0] = constructDataSetForFundPrice(fundPriceHistoryViewList);


        BarChartData barChartData = new BarChartData();
        barChartData.setDatasets(dataSetArray);
        barChartData.setLabels(labelArray);

        String result = new Gson().toJson(barChartData);
        System.out.println("generateBarChartData:" + result);
        return result;
    }

    @Override
    public boolean isValidSessionToken(String token) {
        return customerRepository.getCustomerBySessionToken(token) != null;
    }

    @Override
    public String generateBarChartData() {
        List<Fund> fundList = fundRepository.findAllFund();
        if (Util.isEmptyList(fundList)) {
            return null;
        }
        String[] labelArray = new String[fundList.size()];
        for (int i = 0; i < fundList.size(); i++) {
            labelArray[i] = fundList.get(i).getFundName();
        }
        DataSet[] dataSetArray = new DataSet[1];
        dataSetArray[0] = constructDataSet(fundList);
        BarChartData barChartData = new BarChartData();
        barChartData.setDatasets(dataSetArray);
        barChartData.setLabels(labelArray);
        String result = new Gson().toJson(barChartData);
        System.out.println("generateBarChartData:" + result);
        return result;
    }
    private DataSet constructDataSet(List<Fund> fundList) {
        DataSet dataSet = new DataSet();
        double[] data = new double[fundList.size()];
        for (int i = 0; i < fundList.size(); i++) {
            data[i] = fundList.get(i).getLastPrice() / 1000.0;
        }
        dataSet.setData(data);
        return dataSet;
    }
    private DataSet constructDataSetForFundPrice(List<FundPriceHistoryView> fundList) {
        DataSet dataSet = new DataSet();
        double[] data = new double[fundList.size()];
        for (int i = 0; i < fundList.size(); i++) {
            data[i] = fundList.get(i).getPrice() / 1000.0;
        }
        dataSet.setData(data);
        return dataSet;
    }
}
