/**
 * Copyright (c) 2017 Inocybe Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.sfc.statistics.handlers;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.opendaylight.sfc.provider.api.SfcProviderServiceForwarderAPI;
import org.opendaylight.sfc.statistics.readers.SfcStatisticsReaderBase;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.rendered.service.path.RenderedServicePathHop;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.ss.rev140701.service.statistics.group.StatisticByTimestamp;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.ss.rev140701.service.statistics.group.StatisticByTimestampBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.ss.rev140701.statistic.fields.ServiceStatistic;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.ss.rev140701.statistic.fields.ServiceStatisticBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RspStatisticsHandler extends SfcStatisticsHandlerBase {

    private static final Logger LOG = LoggerFactory.getLogger(RspStatisticsHandler.class);

    public RspStatisticsHandler(SfcStatisticsReaderBase statsReader) {
        super(statsReader);
    }

    @Override
    public <T extends DataObject> List<StatisticByTimestamp> getStatistics(T data) {

        RenderedServicePath rsp = (RenderedServicePath) data;

        LOG.debug("RspStatisticsHandler::getStatistics firing");

        // Need to get the "Next Hop" flows for the first-hop and last-hop of the RSP
        // These will be the RSP in bytes/packets and out bytes/packets respectively
        Optional<RenderedServicePathHop> firstHop = getRspHop(rsp, true);
        if (!firstHop.isPresent()) {
            LOG.warn("RspStatisticsHandler cant get firstHop for RSP [{}]", rsp.getPathId());
            return Collections.emptyList();
        }

        ServiceFunctionForwarder firstHopSff = SfcProviderServiceForwarderAPI.readServiceFunctionForwarder(
                firstHop.get().getServiceFunctionForwarder());
        if (firstHopSff == null) {
            LOG.warn("RspStatisticsHandler cant get firstHopSff for RSP [{}]", rsp.getPathId());
            return Collections.emptyList();
        }

        Optional<RenderedServicePathHop> lastHop = getRspHop(rsp, false);
        if (!lastHop.isPresent()) {
            LOG.warn("RspStatisticsHandler cant get lastHop for RSP [{}]", rsp.getPathId());
            return Collections.emptyList();
        }

        ServiceFunctionForwarder lastHopSff = SfcProviderServiceForwarderAPI.readServiceFunctionForwarder(
                lastHop.get().getServiceFunctionForwarder());
        if (lastHopSff == null) {
            LOG.warn("RspStatisticsHandler cant get lastHopSff for RSP [{}]", rsp.getPathId());
            return Collections.emptyList();
        }

        Optional<ServiceStatistic> inStats = getStatsReader().getNextHopStatistics(
               true, firstHopSff, rsp.getPathId(), firstHop.get().getServiceIndex());
        if (!inStats.isPresent()) {
            LOG.warn("RspStatisticsHandler cant get input stats for RSP [{}]", rsp.getPathId());
            return Collections.emptyList();
        }

        Optional<ServiceStatistic> outStats = getStatsReader().getNextHopStatistics(
                false, lastHopSff, rsp.getPathId(), lastHop.get().getServiceIndex());
        if (!outStats.isPresent()) {
            LOG.warn("RspStatisticsHandler cant get output stats for RSP [{}]", rsp.getPathId());
            return Collections.emptyList();
        }

        ServiceStatisticBuilder srvStatsBuilder = new ServiceStatisticBuilder();
        srvStatsBuilder.setBytesIn(inStats.get().getBytesIn());
        srvStatsBuilder.setPacketsIn(inStats.get().getPacketsIn());
        srvStatsBuilder.setBytesOut(outStats.get().getBytesOut());
        srvStatsBuilder.setPacketsOut(outStats.get().getPacketsOut());

        StatisticByTimestampBuilder statsBuilder = new StatisticByTimestampBuilder();
        statsBuilder.setTimestamp(getTimestampKey().getTimestamp());
        statsBuilder.setKey(getTimestampKey());
        statsBuilder.setServiceStatistic(srvStatsBuilder.build());

        LOG.debug("RspStatisticsHandler::writeStatistics completed with statistics");

        return Collections.singletonList(statsBuilder.build());
    }

    private Optional<RenderedServicePathHop> getRspHop(RenderedServicePath rsp, boolean getFirstHop) {
        List<RenderedServicePathHop> rspHopList = rsp.getRenderedServicePathHop();
        if (rspHopList == null) {
            LOG.warn("RSP Hop list is null for RSP [{}]", rsp.getName().getValue());
            return Optional.empty();
        }

        if (rspHopList.isEmpty()) {
            LOG.warn("RSP Hop list is empty for RSP [{}]", rsp.getName().getValue());
            return Optional.empty();
        }

        // Is it the first or last hop in the RSP?
        RenderedServicePathHop hop;
        if (getFirstHop) {
            hop = rspHopList.get(0);
        } else {
            hop = rspHopList.get(rspHopList.size() - 1);
        }

        if (hop == null) {
            LOG.warn("The RSP Hop is NULL for RSP [{}]", rsp.getName().getValue());
            return Optional.empty();
        }

        if (hop.getServiceFunctionForwarder() == null) {
            LOG.warn("The RSP Hop has no SFF for RSP [{}]", rsp.getName().getValue());
            return Optional.empty();
        }

        if (hop.getServiceIndex() == null) {
            LOG.warn("The RSP Hop has no Service Index for RSP [{}]", rsp.getName().getValue());
            return Optional.empty();
        }

        return Optional.of(hop);
    }
}
