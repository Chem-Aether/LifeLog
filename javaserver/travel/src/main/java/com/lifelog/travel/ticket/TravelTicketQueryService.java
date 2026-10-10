package com.lifelog.travel.ticket;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lifelog.travel.integration.mapserver.GeoReferenceClient;
import com.lifelog.travel.flight.FlightRecord;
import com.lifelog.travel.flight.FlightRecordMapper;
import com.lifelog.travel.flight.FlightRecordService;
import com.lifelog.travel.train.TrainRecord;
import com.lifelog.travel.train.TrainRecordMapper;
import com.lifelog.travel.train.TrainTicketService;
import com.lifelog.travel.train.TrainWaypoint;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TravelTicketQueryService {
    private final TrainRecordMapper trainMapper;
    private final FlightRecordMapper flightMapper;
    private final TrainTicketService trainService;
    private final FlightRecordService flightService;
    private final GeoReferenceClient geoClient;

    public TravelTicketQueryService(TrainRecordMapper trainMapper, FlightRecordMapper flightMapper,
                                    TrainTicketService trainService, FlightRecordService flightService, GeoReferenceClient geoClient) {
        this.trainMapper = trainMapper; this.flightMapper = flightMapper; this.trainService = trainService;
        this.flightService = flightService; this.geoClient = geoClient;
    }

    public List<Map<String,Object>> findTickets(String mode, Integer year) {
        validate(mode, year, true);
        List<Map<String,Object>> result = new ArrayList<>();
        if ("all".equals(mode) || "train".equals(mode)) result.addAll(trainTickets(year));
        if ("all".equals(mode) || "flight".equals(mode)) result.addAll(flightTickets(year));
        result.sort(Comparator.comparing(item -> (LocalDateTime)item.get("sortTime"), Comparator.nullsLast(Comparator.reverseOrder())));
        result.forEach(item -> item.remove("sortTime"));
        return result;
    }

    public Map<String,Object> getTicketSummary(String mode, Integer year) {
        validate(mode, year, false);
        return "train".equals(mode) ? trainSummary(year) : flightSummary(year);
    }

    private List<Map<String,Object>> trainTickets(Integer year) {
        return trainMapper.selectRecords(year).stream().map(trainService::hydrate).map(item -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("ticketType", "train"); row.put("trainId", item.getTrainId()); row.put("Number", item.getTrainNo());
            row.put("From", item.getStartStationName()); row.put("To", item.getEndStationName());
            row.put("time", duration(item.getDepartureTime(), item.getArrivalTime())); row.put("sortTime", item.getDepartureTime());
            row.put("routeGeoJson", item.getRouteGeoJson());
            row.put("routeStations", item.getWaypoints().stream()
                    .filter(point -> point.getLongitude() != null && point.getLatitude() != null)
                    .map(point -> Map.<String,Object>of("name", Objects.toString(point.getStationName(), ""),
                            "longitude", point.getLongitude(), "latitude", point.getLatitude())).toList());
            row.put("more", List.of(detail("发车时间", format(item.getDepartureTime())), detail("到达时间", format(item.getArrivalTime())),
                    detail("类型/车型", String.join(" / ", Objects.toString(item.getTrainType(), ""), Objects.toString(item.getTrainModel(), ""))),
                    detail("里程", item.getMileageKm() == null ? "" : item.getMileageKm() + " km"),
                    detail("席位", Objects.toString(item.getSeatType(), "")), detail("车厢/座位", String.join(" / ",
                            Objects.toString(item.getCarriageNo(), ""), Objects.toString(item.getSeatNo(), "")))));
            return row;
        }).toList();
    }

    private List<Map<String,Object>> flightTickets(Integer year) {
        LocalDateTime start = year == null ? null : LocalDateTime.of(year,1,1,0,0);
        List<FlightRecord> flights = flightMapper.selectList(Wrappers.<FlightRecord>lambdaQuery()
                .ge(start != null, FlightRecord::getDepartureTime, start)
                .lt(start != null, FlightRecord::getDepartureTime, start == null ? null : start.plusYears(1))
                .orderByDesc(FlightRecord::getDepartureTime));
        flights.forEach(flightService::hydrate);
        Set<String> codes = flights.stream().flatMap(item -> java.util.stream.Stream.of(item.getDepartureIcao(), item.getArrivalIcao()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String,GeoReferenceClient.GeoAirport> airports = geoClient.airportsByIcao(codes);
        return flights.stream().map(item -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("ticketType", "flight"); row.put("flightId", item.getFlightId()); row.put("Number", item.getFlightNo());
            row.put("From", item.getDepartureAirportName()); row.put("To", item.getArrivalAirportName());
            row.put("time", duration(item.getDepartureTime(), item.getArrivalTime())); row.put("sortTime", item.getDepartureTime());
            GeoReferenceClient.GeoAirport from = airports.get(item.getDepartureIcao()), to = airports.get(item.getArrivalIcao());
            if (from != null) { row.put("fromLongitude", from.longitude()); row.put("fromLatitude", from.latitude()); }
            if (to != null) { row.put("toLongitude", to.longitude()); row.put("toLatitude", to.latitude()); }
            row.put("more", List.of(detail("起飞时间", format(item.getDepartureTime())), detail("到达时间", format(item.getArrivalTime())),
                    detail("机型", Objects.toString(item.getAircraftType(), "")), detail("座位", Objects.toString(item.getSeatNo(), ""))));
            return row;
        }).toList();
    }

    private Map<String,Object> trainSummary(Integer year) {
        List<TrainRecord> rows = trainMapper.selectRecords(year).stream().map(trainService::hydrate).toList();
        long minutes = rows.stream().mapToLong(item -> minutes(item.getDepartureTime(), item.getArrivalTime())).sum();
        long stations = rows.stream().flatMap(item -> item.getWaypoints().stream()).map(TrainWaypoint::getStationName).filter(Objects::nonNull).distinct().count();
        double mileage = rows.stream().filter(item -> item.getMileageKm() != null).mapToDouble(item -> item.getMileageKm().doubleValue()).sum();
        Map<String,Long> groups = rows.stream().collect(Collectors.groupingBy(item -> Objects.toString(item.getTrainType(), trainPrefix(item.getTrainNo())), Collectors.counting()));
        return summary(List.of(metric("里程", Math.round(mileage) + " km"), metric("时长", duration(minutes)), metric("次数", rows.size()), metric("车站", stations)), groups);
    }

    private Map<String,Object> flightSummary(Integer year) {
        List<FlightRecord> rows = flightTicketsRaw(year);
        long minutes = rows.stream().mapToLong(item -> minutes(item.getDepartureTime(), item.getArrivalTime())).sum();
        double distance = rows.stream().filter(item -> item.getDistanceKm() != null).mapToDouble(item -> item.getDistanceKm().doubleValue()).sum();
        long airports = rows.stream().flatMap(item -> java.util.stream.Stream.of(item.getDepartureIcao(), item.getArrivalIcao())).filter(Objects::nonNull).distinct().count();
        Map<String,Long> groups = rows.stream().collect(Collectors.groupingBy(item -> airlinePrefix(item.getFlightNo()), Collectors.counting()));
        return summary(List.of(metric("航程", Math.round(distance) + " km"), metric("航时", duration(minutes)), metric("航次", rows.size()), metric("航点", airports)), groups);
    }

    private List<FlightRecord> flightTicketsRaw(Integer year) {
        LocalDateTime start = year == null ? null : LocalDateTime.of(year,1,1,0,0);
        return flightMapper.selectList(Wrappers.<FlightRecord>lambdaQuery().ge(start != null, FlightRecord::getDepartureTime, start)
                .lt(start != null, FlightRecord::getDepartureTime, start == null ? null : start.plusYears(1)));
    }
    private Map<String,Object> summary(List<Map<String,Object>> metrics, Map<String,Long> groups) {
        List<Map<String,Object>> distribution = groups.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed())
                .map(entry -> Map.<String,Object>of("name", entry.getKey(), "value", entry.getValue())).toList();
        return Map.of("metrics", metrics, "distribution", distribution);
    }
    private void validate(String mode, Integer year, boolean all) {
        if (!(all ? Set.of("all","train","flight") : Set.of("train","flight")).contains(mode))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的旅行模式");
        if (year != null && (year < 1900 || year > 2100)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "年份超出范围");
    }
    private Map<String,Object> detail(String label, String value) { return Map.of("label", label, "value", value); }
    private Map<String,Object> metric(String label, Object value) { return Map.of("label", label, "value", value); }
    private String format(LocalDateTime value) { return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")); }
    private long minutes(LocalDateTime a, LocalDateTime b) { return a == null || b == null ? 0 : Math.max(0, ChronoUnit.MINUTES.between(a,b)); }
    private String duration(LocalDateTime a, LocalDateTime b) { return duration(minutes(a,b)); }
    private String duration(long value) { return value / 60 + "h" + value % 60 + "m"; }
    private String airlinePrefix(String no) { return no == null || no.length() < 2 ? "其他" : no.substring(0,2); }
    private String trainPrefix(String no) { return no == null || no.isBlank() ? "其他" : no.substring(0,1).toUpperCase(); }
}
