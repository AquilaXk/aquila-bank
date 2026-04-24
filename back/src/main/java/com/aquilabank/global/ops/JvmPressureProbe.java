package com.aquilabank.global.ops;

import java.time.Duration;

public interface JvmPressureProbe {

  JvmPressureSnapshot snapshot(Duration gcWindow);
}
