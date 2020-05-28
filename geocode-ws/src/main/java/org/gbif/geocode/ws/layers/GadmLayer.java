package org.gbif.geocode.ws.layers;

import com.google.inject.Singleton;
import org.gbif.geocode.api.model.Location;
import org.gbif.geocode.ws.model.LocationMapper;
import org.gbif.geocode.ws.service.impl.MyBatisGeocoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

@Singleton
public class GadmLayer extends AbstractBitmapCachedLayer {
  public static Logger LOG = LoggerFactory.getLogger(MyBatisGeocoder.class);

  public GadmLayer() {
    super(GadmLayer.class.getResourceAsStream("gadm.png"));
  }

  @Override
  String name() {
    return "GADM";
  }

  public Collection<Location> checkDatabase(LocationMapper locationMapper, String point, double uncertainty) {
    return locationMapper.listGadm(point, uncertainty);
  }
}
