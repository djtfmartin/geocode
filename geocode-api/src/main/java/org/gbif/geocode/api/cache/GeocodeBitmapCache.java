package org.gbif.geocode.api.cache;

import org.gbif.geocode.api.model.Location;
import org.gbif.geocode.api.service.GeocodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A cache which uses a bitmap to cache coordinate lookups.
 */
public class GeocodeBitmapCache implements GeocodeService {
  public static Logger LOG = LoggerFactory.getLogger(GeocodeBitmapCache.class);

  private final GeocodeService geocodeService;

  // Bitmap image cache
  private final BufferedImage img;
  // Border colour must be queried
  private final static int BORDER = 0x000000;
  // Nothing colour is not part of this layer (e.g. ocean for a land layer)
  private final static int NOTHING = 0xFFFFFF;
  private final int imgWidth;
  private final int imgHeight;
  private final Map<Integer, Collection<Location>> colourKey = new HashMap<>();

  public GeocodeBitmapCache(GeocodeService geocodeService, InputStream bitmap) {
    this.geocodeService = geocodeService;

    try {
      img = ImageIO.read(bitmap);
      imgHeight = img.getHeight();
      imgWidth = img.getWidth();
    } catch (IOException e) {
      throw new RuntimeException("Unable to load map image", e);
    }
  }

  /**
   * Simple get candidates by point.
   */
  @Override
  public Collection<Location> get(Double lat, Double lng, Double uncertainty) {
    Collection<Location> locations = null;

    // Check the image map for a sure location.
    if (uncertainty == null || uncertainty <= 0.05d) {
      locations = getFromBitmap(lat, lng);
    }

    // If that doesn't help, use the database.
    if (locations == null) {
      locations = geocodeService.get(lat, lng, uncertainty);
    }

    return locations;
  }

  @Override
  public byte[] bitmap() {
    return new byte[0];
  }

  /**
   * Check the colour of a pixel from the map image to determine the country.
   * <br/>
   * Other than the special cases, the colours are looked up using the web service the first
   * time they are found.
   * @return Locations or null if the bitmap can't answer.
   */
  protected Collection<Location> getFromBitmap(double lat, double lng) {
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

      case NOTHING:
        return new ArrayList<>();

      default:
        if (!colourKey.containsKey(colour)) {
          locations = geocodeService.get(lat, lng, null);
          // Don't store this if there aren't any locations.
          if (locations.size() == 0) {
            LOG.error("For colour {} (LL {},{}; pixel {},{}) the webservice gave zero locations.", hex, lat, lng, x, y);
          } else {

            // TODO
            // Don't store if the ISO code is -99; this code is used for some exceptional bits of territory (e.g. Baikonur Cosmodrome, the Korean DMZ).
            if ("-99".equals(locations.iterator().next().getIsoCountryCode2Digit())) {
              LOG.info("New colour {} (LL {},{}; pixel {},{}); exceptional territory of {} will not be cached", hex, lat, lng, x, y, joinLocations(locations));
            } else {
              LOG.info("New colour {} (LL {},{}; pixel {},{}); remembering as {}", hex, lat, lng, x, y, joinLocations(locations));
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

  private String joinLocations(Collection<Location> loc) {
    return loc.stream().map(l -> l.getIsoCountryCode2Digit()).distinct().collect(Collectors.joining(", "));
  }
}
