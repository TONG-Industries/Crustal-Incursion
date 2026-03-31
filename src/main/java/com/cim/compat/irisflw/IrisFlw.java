package com.cim.compat.irisflw;

import net.irisshaders.iris.api.v0.IrisApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cim.compat.irisflw.backend.IrisFlwBackends;

public final class IrisFlw {
   public static final String MOD_ID = "irisflw";
   public static final Logger LOGGER = LoggerFactory.getLogger("irisflw");

   public static void init() {
      IrisFlwBackends.init();
   }

   public static boolean isShaderPackInUse() {
      return IrisApi.getInstance().isShaderPackInUse();
   }

   public static boolean isUsingExtendedVertexFormat() {
      return isShaderPackInUse();
   }
}
