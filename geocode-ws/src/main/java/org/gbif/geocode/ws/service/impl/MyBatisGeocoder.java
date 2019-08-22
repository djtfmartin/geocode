package org.gbif.geocode.ws.service.impl;

import org.gbif.api.vocabulary.Country;
import org.gbif.common.parsers.CountryParser;
import org.gbif.common.parsers.core.ParseResult;
import org.gbif.geocode.api.model.Location;
import org.gbif.geocode.api.service.GeocodeService;
import org.gbif.geocode.ws.model.LocationMapper;
import org.gbif.geocode.ws.monitoring.GeocodeWsStatistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link GeocodeService} using MyBatis to search for results.
 */
@Singleton
public class MyBatisGeocoder implements GeocodeService {
  public static Logger LOG = LoggerFactory.getLogger(MyBatisGeocoder.class);

  private final static CountryParser COUNTRY_PARSER = CountryParser.getInstance();

  private final SqlSessionFactory sqlSessionFactory;

  private final GeocodeWsStatistics statistics;

  // Distance are calculated using the approximation that 1 degree is ~ 111 kilometers

  // The default distance is chosen at ~5km to allow for gaps between land and sea (political and EEZ)
  // and to cope with the inaccuracies introduced in the simplified datasets.

  // The ~25km distance is chosen to cope with coastal lakes (except the one in Turkmenistan)

  // 0.05 * 111 km = 5.55 km
  private final static double DEFAULT_DISTANCE = 0.05d;

  // 0.25 * 111 km = 27.75 km
  private final static double LARGER_DISTANCE = 0.25d;

  @Inject
  public MyBatisGeocoder(SqlSessionFactory sqlSessionFactory, GeocodeWsStatistics statistics) {
    this.sqlSessionFactory = sqlSessionFactory;
    this.statistics = statistics;
  }

  /**
   * Simple get candidates by point.
   */
  @Override
  public Collection<Location> get(Double lat, Double lng, Double uncertainty) {
    Collection<Location> locations = new ArrayList<>();

    if (uncertainty == null) uncertainty = DEFAULT_DISTANCE;

    // TODO DOCUMENT
    uncertainty = Math.max(uncertainty, DEFAULT_DISTANCE);

    SqlSession session = sqlSessionFactory.openSession();
    try {
      LocationMapper locationMapper = session.getMapper(LocationMapper.class);
      String point = "POINT(" + lng + ' ' + lat + ')';

      Optional<List<Location>> optLocations = tryWithin(point, uncertainty, locationMapper);
      if (optLocations.isPresent()) {
        statistics.foundWithin5Km();
        locations.addAll(optLocations.get());
      } else {
        optLocations = tryWithin(point, LARGER_DISTANCE, locationMapper);
        if (optLocations.isPresent()) {
          statistics.foundWithin5Km();
          locations.addAll(optLocations.get());
          LOG.warn("Had to use larger distance {} to resolve {}", LARGER_DISTANCE, point);
        } else {
          statistics.noResult();
        }
      }

      statistics.servedFromDatabase();
    } finally {
      session.close();
    }

    statistics.resultSize(locations.size());
    return locations;
  }

  /**
   * Searches the political and EEZ locations within a distance.
   */
  private Optional<List<Location>> tryWithin(String point, double distance, LocationMapper locationMapper) {
    List<Location> locations = new ArrayList<>();

    List<Location> politicalLocations = locationMapper.listPolitical(point, distance);
    if (!politicalLocations.isEmpty()) {
      statistics.foundPolitical();
      locations.addAll(politicalLocations);
    }

    List<Location> eezLocations = locationMapper.listEez(point, distance);
    if (!eezLocations.isEmpty()) {
      statistics.foundEez();
      fixEezIsoCodes(eezLocations);
      locations.addAll(eezLocations);
    }

    if (locations.isEmpty()) {
      return Optional.absent();
    } else {
      return Optional.of(locations);
    }
  }

  /**
   * Some EEZ locations won't have ISO countries, so need to fill them in based on returned title.
   * <p/>
   * This will change the objects in place.
   *
   * @param locations list of locations to fix.
   */
  private static void fixEezIsoCodes(@Nullable Collection<Location> locations) {
    if (locations == null || locations.isEmpty()) {
      return;
    }

    for (Location loc : locations) {
      if (loc.getIsoCountryCode2Digit() == null) {
        ParseResult<Country> result = COUNTRY_PARSER.parse(loc.getTitle());
        if (result.isSuccessful()) {
          loc.setIsoCountryCode2Digit(result.getPayload().getIso2LetterCode());
        }
      }
    }
  }

  @Override
  public byte[] bitmap() {
    throw new UnsupportedOperationException("Not implemented.");
  }
}
