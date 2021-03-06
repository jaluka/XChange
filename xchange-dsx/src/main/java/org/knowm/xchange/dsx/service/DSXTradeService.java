package org.knowm.xchange.dsx.service;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dsx.DSXAdapters;
import org.knowm.xchange.dsx.DSXAuthenticatedV2;
import org.knowm.xchange.dsx.DSXExchange;
import org.knowm.xchange.dsx.dto.marketdata.DSXExchangeInfo;
import org.knowm.xchange.dsx.dto.trade.DSXCancelAllOrdersResult;
import org.knowm.xchange.dsx.dto.trade.DSXCancelOrderResult;
import org.knowm.xchange.dsx.dto.trade.DSXOrder;
import org.knowm.xchange.dsx.dto.trade.DSXTradeHistoryResult;
import org.knowm.xchange.dsx.dto.trade.DSXTradeResult;
import org.knowm.xchange.dsx.dto.trade.DSXTransHistoryResult;
import org.knowm.xchange.dsx.service.trade.params.DSXTradeHistoryParams;
import org.knowm.xchange.dsx.service.trade.params.DSXTransHistoryParams;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrency;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParamPaging;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsIdSpan;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.utils.DateUtils;

/**
 * @author Mikhail Wall
 */
public class DSXTradeService extends DSXTradeServiceRaw implements TradeService {

  /**
   * Constructor
   *
   * @param exchange
   */
  public DSXTradeService(Exchange exchange) {

    super(exchange);
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return getOpenOrders(createOpenOrdersParams());
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params)
      throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {

    Map<Long, DSXOrder> orders = getDSXActiveOrders(null);
    return DSXAdapters.adaptOrders(orders);

  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {

    DSXExchangeInfo dsxExchangeInfo = ((DSXExchange) exchange).getDsxExchangeInfo();
    LimitOrder order = DSXAdapters.createLimitOrder(marketOrder, dsxExchangeInfo);
    return placeLimitOrder(order);
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {

    DSXOrder.Type type = limitOrder.getType() == Order.OrderType.BID ? DSXOrder.Type.buy : DSXOrder.Type.sell;

    String pair = DSXAdapters.getPair(limitOrder.getCurrencyPair());

    DSXOrder dsxOrder = new DSXOrder(pair, type, limitOrder.getTradableAmount(), limitOrder.getLimitPrice(), limitOrder.getTimestamp().getTime(),
        3, DSXOrder.OrderType.limit);

    DSXTradeResult result = tradeDSX(dsxOrder);
    return Long.toString(result.getOrderId());
  }

  @Override
  public boolean cancelOrder(String orderId) throws IOException {

    DSXCancelOrderResult ret = cancelDSXOrder(Long.parseLong(orderId));
    return (ret != null);
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    if (orderParams instanceof CancelOrderByIdParams) {
      cancelOrder(((CancelOrderByIdParams) orderParams).orderId);
    }
    return false;
  }

  public boolean cancelAllOrders() throws IOException {

    DSXCancelAllOrdersResult ret = cancelAllDSXOrders();
    return (ret != null);
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {

    Long count = null;
    Long fromId = null;
    Long endId = null;
    DSXAuthenticatedV2.SortOrder sort = DSXAuthenticatedV2.SortOrder.DESC;
    Long since = null;
    Long end = null;
    String dsxpair = null;

    if (params instanceof TradeHistoryParamPaging) {
      TradeHistoryParamPaging pagingParams = (TradeHistoryParamPaging) params;
      Integer pageLength = pagingParams.getPageLength();
      Integer pageNumber = pagingParams.getPageNumber();
      if (pageNumber == null) {
        pageNumber = 0;
      }

      if (pageLength != null) {
        count = pageLength.longValue();
      }
    }

    if (params instanceof TradeHistoryParamsIdSpan) {
      TradeHistoryParamsIdSpan idParams = (TradeHistoryParamsIdSpan) params;
      fromId = nullSafeToLong(idParams.getStartId());
      endId = nullSafeToLong(idParams.getEndId());
    }

    if (params instanceof TradeHistoryParamsTimeSpan) {
      TradeHistoryParamsTimeSpan timeParams = (TradeHistoryParamsTimeSpan) params;
      since = nullSafeUnixTime(timeParams.getStartTime());
      end = nullSafeUnixTime(timeParams.getEndTime());
    }

    if (params instanceof TradeHistoryParamCurrencyPair) {
      CurrencyPair pair = ((TradeHistoryParamCurrencyPair) params).getCurrencyPair();
      if (pair != null) {
        dsxpair = DSXAdapters.getPair(pair);
      }
    }

    if (params instanceof DSXTransHistoryParams) {
      sort = ((DSXTransHistoryParams) params).getSortOrder();
    }

    Map<Long, DSXTradeHistoryResult> resultMap = getDSXTradeHistory(count, fromId, endId, sort, since, end, dsxpair);
    return DSXAdapters.adaptTradeHistory(resultMap);
  }

  private static Long nullSafeToLong(String str) {

    try {
      return (str == null || str.isEmpty()) ? null : Long.valueOf(str);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Long nullSafeUnixTime(Date time) {

    return time != null ? DateUtils.toUnixTime(time) : null;
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {

    return new DSXTradeHistoryParams();
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {

    return new DefaultOpenOrdersParamCurrencyPair();
  }

  @Override
  public Collection<Order> getOrder(String... orderIds) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    throw new NotYetImplementedForExchangeException();
  }

  public Map<Long, DSXTransHistoryResult> getTransHistory(DSXTransHistoryParams params) throws ExchangeException, IOException {

    Long count = null;
    Long startId = null;
    Long endId = null;
    DSXAuthenticatedV2.SortOrder sort = DSXAuthenticatedV2.SortOrder.DESC;
    Long startTime = null;
    Long endTime = null;
    DSXTransHistoryResult.Type type = null;
    DSXTransHistoryResult.Status status = null;
    String currency = null;

    if (params instanceof TradeHistoryParamPaging) {
      TradeHistoryParamPaging pagingParams = params;
      Integer pageLength = pagingParams.getPageLength();

      if (pageLength != null) {
        count = pageLength.longValue();
      }
    }

    if (params instanceof TradeHistoryParamsIdSpan) {
      TradeHistoryParamsIdSpan idParams = params;
      startId = nullSafeToLong(idParams.getStartId());
      endId = nullSafeToLong(idParams.getEndId());
    }

    if (params instanceof TradeHistoryParamsTimeSpan) {
      TradeHistoryParamsTimeSpan timeParams = params;
      startTime = nullSafeUnixTime(timeParams.getStartTime());
      endTime = nullSafeUnixTime(timeParams.getEndTime());
    }

    if (params instanceof DSXTransHistoryParams) {
      sort = params.getSortOrder();
      status = params.getStatus();
      type = params.getType();
    }

    if (params instanceof TradeHistoryParamCurrency) {
      Currency c = ((TradeHistoryParamCurrency) params).getCurrency();
      currency = c == null ? null : c.getCurrencyCode();
    }

    return getDSXTransHistory(count, startId, endId, sort, startTime, endTime, type, status, currency);
  }
}
