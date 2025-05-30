package gov.noaa.pfel.erddap.handlers;

import static gov.noaa.pfel.erddap.dataset.EDDGrid.DEFAULT_MATCH_AXIS_N_DIGITS;
import static gov.noaa.pfel.erddap.dataset.EDDGridFromFiles.MF_LAST;

import com.cohort.util.SimpleException;
import com.cohort.util.String2;
import gov.noaa.pfel.erddap.dataset.*;
import gov.noaa.pfel.erddap.util.EDStatic;
import gov.noaa.pfel.erddap.variable.EDVAlt;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class EDDGridFromFilesHandler extends BaseGridHandler {
  private final String datasetType;

  public EDDGridFromFilesHandler(
      SaxHandler saxHandler, String datasetID, State completeState, String datasetType) {
    super(saxHandler, datasetID, completeState);
    this.datasetType = datasetType;
  }

  private boolean tFileTableInMemory = false;
  private int tUpdateEveryNMillis = 0;
  private String tFileDir = null;
  private String tFileNameRegex = ".*";
  private boolean tRecursive = false;
  private String tPathRegex = ".*";
  private boolean tAccessibleViaFiles = EDStatic.config.defaultAccessibleViaFiles;
  private String tMetadataFrom = MF_LAST;
  private int tMatchAxisNDigits = DEFAULT_MATCH_AXIS_N_DIGITS;
  private String tCacheFromUrl = null;
  private int tCacheSizeGB = -1;
  private String tCachePartialPathRegex = null;

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes)
      throws SAXException {
    handleAttributes(localName);
    handleDataVariables(localName);
    handleAxisVariable(localName);
    if ("altitudeMetersPerSourceUnit".equals(localName)) {
      throw new SimpleException(EDVAlt.stopUsingAltitudeMetersPerSourceUnit);
    }
  }

  @Override
  protected boolean handleEndElement(String contentStr, String localName) {
    if (super.handleEndElement(contentStr, localName)) {
      return true;
    }
    switch (localName) {
      case "updateEveryNMillis" -> tUpdateEveryNMillis = String2.parseInt(contentStr);
      case "fileDir" -> tFileDir = contentStr;
      case "fileNameRegex" -> tFileNameRegex = contentStr;
      case "recursive" -> tRecursive = String2.parseBoolean(contentStr);
      case "pathRegex" -> tPathRegex = contentStr;
      case "accessibleViaFiles" -> tAccessibleViaFiles = String2.parseBoolean(contentStr);
      case "metadataFrom" -> tMetadataFrom = contentStr;
      case "fileTableInMemory" -> tFileTableInMemory = String2.parseBoolean(contentStr);
      case "matchAxisNDigits" ->
          tMatchAxisNDigits = String2.parseInt(contentStr, DEFAULT_MATCH_AXIS_N_DIGITS);
      case "ensureAxisValuesAreEqual" ->
          tMatchAxisNDigits = String2.parseBoolean(contentStr) ? 20 : 0;
      case "cacheFromUrl" -> tCacheFromUrl = contentStr;
      case "cacheSizeGB" -> tCacheSizeGB = String2.parseInt(contentStr);
      case "cachePartialPathRegex" -> tCachePartialPathRegex = contentStr;
      default -> {
        return false;
      }
    }
    return true;
  }

  private EDD getDataset() throws Throwable {

    return switch (datasetType) {
      case "EDDGridFromAudioFiles" ->
          new EDDGridFromAudioFiles(
              datasetID,
              tAccessibleTo,
              tGraphsAccessibleTo,
              tAccessibleViaWMS,
              tOnChange,
              tFgdcFile,
              tIso19115File,
              tDefaultDataQuery,
              tDefaultGraphQuery,
              tGlobalAttributes,
              tAxisVariables,
              tDataVariables,
              tReloadEveryNMinutes,
              tUpdateEveryNMillis,
              tFileDir,
              tFileNameRegex,
              tRecursive,
              tPathRegex,
              tMetadataFrom,
              tMatchAxisNDigits,
              tFileTableInMemory,
              tAccessibleViaFiles,
              tnThreads,
              tDimensionValuesInMemory,
              tCacheFromUrl,
              tCacheSizeGB,
              tCachePartialPathRegex);
      case "EDDGridFromNcFiles" ->
          new EDDGridFromNcFiles(
              datasetID,
              tAccessibleTo,
              tGraphsAccessibleTo,
              tAccessibleViaWMS,
              tOnChange,
              tFgdcFile,
              tIso19115File,
              tDefaultDataQuery,
              tDefaultGraphQuery,
              tGlobalAttributes,
              tAxisVariables,
              tDataVariables,
              tReloadEveryNMinutes,
              tUpdateEveryNMillis,
              tFileDir,
              tFileNameRegex,
              tRecursive,
              tPathRegex,
              tMetadataFrom,
              tMatchAxisNDigits,
              tFileTableInMemory,
              tAccessibleViaFiles,
              tnThreads,
              tDimensionValuesInMemory,
              tCacheFromUrl,
              tCacheSizeGB,
              tCachePartialPathRegex);
      case "EDDGridFromNcFilesUnpacked" ->
          new EDDGridFromNcFilesUnpacked(
              datasetID,
              tAccessibleTo,
              tGraphsAccessibleTo,
              tAccessibleViaWMS,
              tOnChange,
              tFgdcFile,
              tIso19115File,
              tDefaultDataQuery,
              tDefaultGraphQuery,
              tGlobalAttributes,
              tAxisVariables,
              tDataVariables,
              tReloadEveryNMinutes,
              tUpdateEveryNMillis,
              tFileDir,
              tFileNameRegex,
              tRecursive,
              tPathRegex,
              tMetadataFrom,
              tMatchAxisNDigits,
              tFileTableInMemory,
              tAccessibleViaFiles,
              tnThreads,
              tDimensionValuesInMemory,
              tCacheFromUrl,
              tCacheSizeGB,
              tCachePartialPathRegex);
      case "EDDGridFromMergeIRFiles" ->
          new EDDGridFromMergeIRFiles(
              datasetID,
              tAccessibleTo,
              tGraphsAccessibleTo,
              tAccessibleViaWMS,
              tOnChange,
              tFgdcFile,
              tIso19115File,
              tDefaultDataQuery,
              tDefaultGraphQuery,
              tGlobalAttributes,
              tAxisVariables,
              tDataVariables,
              tReloadEveryNMinutes,
              tUpdateEveryNMillis,
              tFileDir,
              tFileNameRegex,
              tRecursive,
              tPathRegex,
              tMetadataFrom,
              tMatchAxisNDigits,
              tFileTableInMemory,
              tAccessibleViaFiles,
              tnThreads,
              tDimensionValuesInMemory,
              tCacheFromUrl,
              tCacheSizeGB,
              tCachePartialPathRegex);
      default ->
          throw new Exception(
              "type=\"" + datasetType + "\" needs to be added to EDDGridFromFiles.fromXml at end.");
    };
  }

  @Override
  protected EDD buildDataset() throws Throwable {
    return getDataset();
  }
}
