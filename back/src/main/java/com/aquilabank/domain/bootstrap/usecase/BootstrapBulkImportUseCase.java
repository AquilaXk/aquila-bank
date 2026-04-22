package com.aquilabank.domain.bootstrap.usecase;

import com.aquilabank.domain.bootstrap.model.BootstrapBulkImportCommand;
import com.aquilabank.domain.bootstrap.model.BootstrapBulkImportResult;

/** 내부 bootstrap bulk import 진입점 */
public interface BootstrapBulkImportUseCase {

  BootstrapBulkImportResult importItems(BootstrapBulkImportCommand command);
}
