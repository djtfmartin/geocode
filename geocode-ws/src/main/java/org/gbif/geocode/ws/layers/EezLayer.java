package org.gbif.geocode.ws.layers;

import org.gbif.geocode.ws.service.impl.MyBatisGeocoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EezLayer extends AbstractBitmapCachedLayer {
  public static Logger LOG = LoggerFactory.getLogger(MyBatisGeocoder.class);

  public EezLayer() {
    super(EezLayer.class.getResourceAsStream("eez.png"), 3);
  }

  @Override
  public String name() {
    return "EEZ";
  }
}
