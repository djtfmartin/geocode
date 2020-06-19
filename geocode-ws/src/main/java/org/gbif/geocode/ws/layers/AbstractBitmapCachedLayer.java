package org.gbif.geocode.ws.layers;

import com.google.common.base.Stopwatch;
import org.gbif.geocode.api.model.Location;
import org.gbif.geocode.ws.model.LocationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a single geo-layer and its bitmap cache.
 */
public abstract class AbstractBitmapCachedLayer {
  private Logger LOG = LoggerFactory.getLogger(getClass());

  // Bitmap image cache
  private final BufferedImage img;
  // Border colour must be queried
  private final static int BORDER = 0x000000;
  // Empty colour is not part of this layer (e.g. ocean for a land layer)
  private final static int EMPTY = 0xFFFFFF;
  private final int imgWidth;
  private final int imgHeight;
  // Maximum number of locations in a coloured part of the map
  private final int maxLocations;
  private final Map<Integer, Collection<Location>> colourKey = new HashMap<>();

  public AbstractBitmapCachedLayer(InputStream bitmap) {
    this(bitmap, 1);
  }

  public AbstractBitmapCachedLayer(InputStream bitmap, int maxLocations) {
    try {
      img = ImageIO.read(bitmap);
      imgHeight = img.getHeight();
      imgWidth = img.getWidth();
      this.maxLocations = maxLocations;
    } catch (IOException e) {
      throw new RuntimeException("Unable to load map image", e);
    }
  }

  abstract String name();

  public abstract Collection<Location> checkDatabase(LocationMapper locationMapper, String point, double uncertainty);

  /**
   * Simple get candidates by point.
   */
  public Collection<Location> get(LocationMapper locationMapper, Double lat, Double lng, Double uncertainty) {
    Collection<Location> locations = null;

    // Check the image map for a sure location.
    if (uncertainty == null || uncertainty <= 0.05d) {
      locations = getFromBitmap(locationMapper, lat, lng);
    }

    // If that doesn't help, use the database.
    if (locations == null) {
      String point = "POINT(" + lng + ' ' + lat + ')';
      locations = getFromDatabase(locationMapper, point, uncertainty);
    }

    return locations;
  }

  /**
   * Check the colour of a pixel from the map image to determine the country.
   * <br/>
   * Other than the special cases, the colours are looked up using the database the first
   * time they are found.
   * @return Locations or null if the bitmap can't answer.
   */
  protected Collection<Location> getFromBitmap(LocationMapper locationMapper, double lat, double lng) {
    // Convert the latitude and longitude to x,y coordinates on the image.
    // The axes are swapped, and the image's origin is the top left.
    int x = (int) (Math.round ((lng+180d)/360d*(imgWidth -1)));
    int y = imgHeight -1 - (int) (Math.round ((lat+90d)/180d*(imgHeight -1)));

    int colour = img.getRGB(x, y) & 0x00FFFFFF; // Ignore possible transparency.

    String hex = String.format("#%06x", colour);
    LOG.debug("LatLong {},{} has pixel {},{} with colour {}", lat, lng, x, y, hex);

    Collection<Location> locations;

    switch (colour) {
      case BORDER:
        return null;

      case EMPTY:
        return Collections.EMPTY_LIST;

      default:
        if (!colourKey.containsKey(colour)) {
          String point = "POINT(" + lng + ' ' + lat + ')';
          locations = getFromDatabase(locationMapper, point, 0);
          // Don't store this if there aren't any locations.
          if (locations.size() == 0) {
            LOG.error("For colour {} (LL {},{}; pixel {},{}) the webservice gave zero locations.", hex, lat, lng, x, y);
          } else {
            if (countLocations(locations) > maxLocations) {
              LOG.error("More than {} locations for a colour! {} (LL {},{}; pixel {},{}); locations {}",
                maxLocations, hex, lat, lng, x, y, joinLocations(locations));
            } else {
              LOG.info("New colour {} (LL {},{}; pixel {},{}); remembering as {}",
                hex, lat, lng, x, y, joinLocations(locations));
              colourKey.put(colour, locations);
            }
          }
        } else {
          locations = colourKey.get(colour);
          LOG.debug("Known colour {} (LL {},{}; pixel {},{}) is {}", hex, lat, lng, x, y, joinLocations(locations));
        }
    }

    return locations;
  }

  protected Collection<Location> getFromDatabase(LocationMapper locationMapper, String point, double uncertainty) {
    Stopwatch sw = Stopwatch.createStarted();
    Collection<Location> locations = checkDatabase(locationMapper, point, uncertainty);
    LOG.info("Time for {} is {}", name(), sw.stop());
    return locations;
  }

  private long countLocations(Collection<Location> loc) {
    return loc.stream().map(l -> l.getId()).distinct().count();
  }

  /**
   * Only used for the log message.
   */
  private String joinLocations(Collection<Location> loc) {
    return loc.stream().map(l -> l.getId()).distinct().collect(Collectors.joining(", "));
  }
}
