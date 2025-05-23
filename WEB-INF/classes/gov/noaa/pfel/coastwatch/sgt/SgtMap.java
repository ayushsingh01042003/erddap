/*
 * SgtMap Copyright 2005, NOAA.
 * See the LICENSE.txt file in this file's directory.
 */
package gov.noaa.pfel.coastwatch.sgt;

import com.cohort.array.*;
import com.cohort.util.File2;
import com.cohort.util.Math2;
import com.cohort.util.MustBe;
import com.cohort.util.SimpleException;
import com.cohort.util.String2;
import com.cohort.util.Test;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import gov.noaa.pfel.coastwatch.griddata.DataHelper;
import gov.noaa.pfel.coastwatch.griddata.FileNameUtility;
import gov.noaa.pfel.coastwatch.griddata.Grid;
import gov.noaa.pfel.coastwatch.pointdata.Table;
import gov.noaa.pfel.coastwatch.util.SSR;
import gov.noaa.pfel.erddap.util.EDStatic;
import gov.noaa.pfel.erddap.util.Metrics;
import gov.noaa.pmel.sgt.*;
import gov.noaa.pmel.sgt.dm.*;
import gov.noaa.pmel.util.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import javax.imageio.ImageIO;

/**
 * This class draws an SgtMap. A note about coordinates:
 *
 * <ul>
 *   <li>JPane - uses "device" coordinates (e.g., pixels, ints, 0,0 at upper left).
 *   <li>Layer - same size as JPane, uses "physical" coordinates (doubles, 0,0 at lower left). It
 *       can be in pixels (but with 0,0 at lower left).
 *   <li>Graph - uses "user" coordinates (e.g., lat and lon), but x/yPhysRange maps user coordinates
 *       to JPane/device coordinates.
 * </ul>
 */
public class SgtMap {

  /**
   * Set this to true (by calling verbose=true in your program, not by changing the code here) if
   * you want lots of diagnostic messages sent to String2.log.
   */
  public static boolean verbose = false;

  /**
   * Set this to true (by calling reallyVerbose=true in your program, not by changing the code here)
   * if you want lots and lots of diagnostic messages sent to String2.log.
   */
  public static boolean reallyVerbose = false;

  /** The font family to use. */
  public static String fontFamily =
      "DejaVu Sans"; // "DejaVu Sans" "Bitstream Vera Sans"; //"LucidaSansRegular", //"Luxi Sans",

  // //"Dialog"; //"Lucida Sans"; //"SansSerif";

  public static String fullPrivateDirectory = SSR.getTempDirectory();
  public static final double defaultAxisLabelHeight = SgtUtil.DEFAULT_AXIS_LABEL_HEIGHT;
  public static final double defaultLabelHeight = SgtUtil.DEFAULT_LABEL_HEIGHT;

  public static final Color oceanColor = new Color(128, 128, 128);
  public static final Color landColor = new Color(204, 204, 204); // lynn uses 191
  public static final Color landMaskStrokeColor = Color.DARK_GRAY; // is 64,64,64
  public static final Color nationsColor = Color.DARK_GRAY;
  public static final Color statesColor =
      new Color(144, 144, 144); // 119, 0, 119); //192, 64, 192); //128, 32, 32);
  public static final Color riversColor =
      new Color(122, 170, 210); // matches ocean.cpt and topography.cpt
  public static final Color lakesColor = riversColor;
  public static boolean drawPoliticalBoundaries =
      true; // a kill switch for nation and state boundaries
  public static final int NO_LAKES_AND_RIVERS = 0; // used for drawLakesAndRivers
  public static final int STROKE_LAKES_AND_RIVERS = 1; // strokes lakes and rivers
  public static final int FILL_LAKES_AND_RIVERS = 2; // fills+strokes lakes, strokes rivers
  public static final ImmutableList<String> drawLandMask_OPTIONS =
      ImmutableList.of("", "under", "over", "outline", "off");

  public static final double PDF_FONTSCALE = 1.5;
  public static final int FULL_RESOLUTION = 0;
  public static final int HIGH_RESOLUTION = 1;
  public static final int INTERMEDIATE_RESOLUTION = 2;
  public static final int LOW_RESOLUTION = 3;
  public static final int CRUDE_RESOLUTION = 4;

  private static final int fRes = FULL_RESOLUTION;
  private static final int hRes = HIGH_RESOLUTION;
  private static final int iRes = INTERMEDIATE_RESOLUTION;
  private static final int lRes = LOW_RESOLUTION;
  private static final int cRes = CRUDE_RESOLUTION;
  // retired 2014-01-09    private final static double maxRanges[]       = {1280, 640, 320, 160,
  // 80,  40,  16,   8,   4,  1.6,  0.8,  0.4,   .16, .08,  .04,  .016, 0};
  // note that                              e.g., 5-10X because here the #'s are ints e.g. 147 |
  // 4-8X because here they #'s are floats e.g., 147.45
  private static final double maxRanges[] = {
    900, 450, 200, 100, 50, 20, 10, 5, 1.6, .8, 0.4, .16, .08, .04, .016, 0
  };
  private static final double majorIncrements[] = {
    180, 90, 45, 20, 10, 5, 2, 1, .5, .2, .1, .05, .02, .01, .005, .002
  }; // if decimal deg axis
  private static final double minorIncrements[] = {
    45, 30, 15, 5, 2, 1, .5, .2, .1, .05, .02, .01, .005, .002, .001, .0005
  }; // if decimal deg axis
  private static final int boundaryResolutions[] = {
    cRes, cRes, cRes, cRes, cRes, lRes, lRes, iRes, iRes, hRes, hRes, hRes, fRes, fRes, fRes, fRes,
    fRes
  };

  /**
   * The nationalBoundary and stateBoundary files must be in the refDirectory. "gshhs_?.b"
   * (?=f|h|i|l|c) files. The files are from the GSHHS project
   * (https://www.ngdc.noaa.gov/mgg/shorelines/gshhs.html). landMaskDir should have slash at end.
   */
  public static final String fullRefDirectory = File2.getRefDirectory();

  // some of this information is in DataSet.properties too, see BAthymFGDC
  public static final String etopoFileName = "etopo1_ice_g_i2.bin";
  public static final String fullEtopoFileName = fullRefDirectory + etopoFileName;
  public static final String BATHYMETRY_BOLD_TITLE =
      "Bathymetry, ETOPO1, 0.0166667 degrees, Global (Ice Sheet Surface)"; // Grid Registered)";
  public static final String BATHYMETRY_SUMMARY =
      "ETOPO1 is a 1 arc-minute global relief model of Earth's surface that integrates "
          + "land topography and ocean bathymetry. It was built from numerous global and "
          + "regional data sets. This is the 'Ice Surface' version, with the top of the "
          + "Antarctic and Greenland ice sheets. The horizontal datum is WGS-84, the vertical "
          + "datum is Mean Sea Level. Keywords: Bathymetry, Digital Elevation. "
          + "This is the grid/node-registered version: the dataset's latitude and longitude "
          + "values mark the centers of the cells.";
  public static final String BATHYMETRY_SOURCE_URL =
      "https://www.ngdc.noaa.gov/mgg/global/relief/ETOPO1/data/ice_surface/grid_registered/binary/etopo1_ice_g_i2.zip";
  public static final String BATHYMETRY_CITE =
      "Amante, C. and B. W. Eakins, ETOPO1 1 Arc-Minute Global Relief Model: "
          + "Procedures, Data Sources and Analysis. NOAA Technical Memorandum NESDIS NGDC-24, "
          + "19 pp, March 2009.";
  public static final String BATHYMETRY_STANDARD_NAME = "altitude";
  public static final String BATHYMETRY_7NAME = "LBAthym";
  public static final String BATHYMETRY_COURTESY = "NOAA NGDC ETOPO1";
  public static final String BATHYMETRY_UNITS = "m";
  public static final String BATHYMETRY_LINES_AT = "-100, -300, -1000, -2000, -4000, -8000";

  /**
   * bathymetryCpt is used to draw bathymetry colors on maps (it draws over land to avoid
   * differences from GSHHS!). The True version stops at -1, treats land as NaN, and is used for
   * transparent .png's. File must be in the gov/noaa/pfel/coastwatch/sgt directory.
   */
  public static final String bathymetryCpt = "Ocean.cpt";

  public static final String bathymetryCptTrue = "OceanTrue.cpt";
  public static final URL bathymetryCptFullName =
      Resources.getResource("gov/noaa/pfel/coastwatch/sgt/" + bathymetryCpt);
  public static URL bathymetryCptTrueFullName =
      Resources.getResource("gov/noaa/pfel/coastwatch/sgt/" + bathymetryCptTrue);

  public static final String TOPOGRAPHY_BOLD_TITLE =
      "Topography, ETOPO1, 0.0166667 degrees, Global (Ice Sheet Surface)"; // grid registered
  public static final String TOPOGRAPHY_SUMMARY = BATHYMETRY_SUMMARY;
  public static final String TOPOGRAPHY_SOURCE_URL = BATHYMETRY_SOURCE_URL;
  public static final String TOPOGRAPHY_CITE = BATHYMETRY_CITE;
  public static final String TOPOGRAPHY_STANDARD_NAME = BATHYMETRY_STANDARD_NAME;
  public static final String TOPOGRAPHY_7NAME = "LBAtopo";
  public static final String TOPOGRAPHY_COURTESY = BATHYMETRY_COURTESY;
  public static final String TOPOGRAPHY_UNITS = BATHYMETRY_UNITS;

  /**
   * topographyCpt is used to draw bathymetry+topography colors on maps. File must be in the
   * gov/noaa/pfel/coastwatch/sgt directory.
   */
  public static final String topographyCpt = "Topography.cpt";

  public static final URL topographyCptFullName =
      Resources.getResource("gov/noaa/pfel/coastwatch/sgt/" + topographyCpt);

  public static final Boundaries nationalBoundaries = Boundaries.getNationalBoundaries();
  public static final Boundaries stateBoundaries = Boundaries.getStateBoundaries();
  public static final Boundaries rivers = Boundaries.getRivers();

  /**
   * This suggests the appropriate maxRange category.
   *
   * @param maxRange the larger of xMax-xMin and yMax-yMin.
   * @param mapSizePixels the length of the larger edge of the map
   * @return the appropriate maxRange category. bigger leads to more labels
   */
  private static int suggestMaxRangeCategory(double maxRange, int mapSizePixels) {
    // max labels in x axis is 10
    // examples:       pacrim=160, nepac=110, westus=22, nanoos=9, nw01=3.5
    int category;
    for (category = 0; category < maxRanges.length - 1; category++)
      if (maxRange >= maxRanges[category]) break;
    category =
        Math2.minMax(
            0,
            maxRanges.length - 1,
            mapSizePixels <= 300
                ? category - 1
                : // adjust for small maps, e.g., 270
                mapSizePixels <= 800
                    ? category
                    : // normal = 450
                    mapSizePixels <= 1600
                        ? category + 1
                        : // big = 900
                        category + 2); // adjust for huge maps (e.g., pdfs) e.g., 2000
    return category;
  }

  /** This suggests the majorMinorCategory. */
  private static int suggestMajorMinorCategory(
      double maxRange, int mapSizePixels, double fontScale) {
    int category = suggestMaxRangeCategory(maxRange, mapSizePixels);

    return Math2.minMax(
        0,
        maxRanges.length - 1, // adjust for fontScale
        fontScale >= 3
            ? category - 2
            : // aim at fontScale 4
            fontScale >= 1.5
                ? category - 1
                : // aim at fontScale 2
                fontScale >= 0.75
                    ? category
                    : // aim at fontScale 1
                    fontScale >= 0.37
                        ? category + 1
                        : // aim at fontScale 0.5
                        category + 2); // aim at fontScale 0.25
  }

  /**
   * This suggests the appropriate shoreline and political boundary resolution (a RESOLUTION
   * constant).
   *
   * @param maxRange the larger of xMax-xMin and yMax-yMin.
   * @param mapSizePixels the length of the larger edge of the map
   * @param boundaryResAdjust allows you to bump the RESOLUTION up or down a notch. E.g., -1 moves
   *     closer to FULL_RESOLUTION, +1 moves closer to CRUDE_RESOLUTION.
   * @return the appropriate shoreline and political boundary resolution (a RESOLUTION constant)
   */
  public static int suggestBoundaryResolution(
      double maxRange, int mapSizePixels, int boundaryResAdjust) {

    int res = boundaryResolutions[suggestMaxRangeCategory(maxRange, mapSizePixels)];
    return Math2.minMax(FULL_RESOLUTION, CRUDE_RESOLUTION, res + boundaryResAdjust);
  }

  /**
   * This suggests the appropriate majorIncrement (distance between labels in axis units).
   *
   * @param maxRange the larger of xMax-xMin and yMax-yMin.
   * @param mapSizePixels the length of the larger edge of the map
   * @param fontScale
   * @return the suggested majorIncrement (distance between labels in axis units).
   */
  public static double suggestMajorIncrement(double maxRange, int mapSizePixels, double fontScale) {
    return majorIncrements[suggestMajorMinorCategory(maxRange, mapSizePixels, fontScale)];
  }

  /**
   * This suggests the appropriate majorIncrement (distance between labels in axis units).
   *
   * @param maxRange the larger of xMax-xMin and yMax-yMin.
   * @param mapSizePixels the length of the larger edge of the map
   * @param fontScale
   * @return the suggested distance between minor ticks in axis units.
   */
  public static double suggestMinorIncrement(double maxRange, int mapSizePixels, double fontScale) {
    return minorIncrements[suggestMajorMinorCategory(maxRange, mapSizePixels, fontScale)];
  }

  /**
   * This suggests the appropriate majorIncrement (distance between labels in axis units).
   *
   * @param maxRange the larger of xMax-xMin and yMax-yMin.
   * @param mapSizePixels the length of the larger edge of the map
   * @param fontScale
   * @return the appropriate distance between vectors in axis units.
   */
  public static double suggestVectorIncrement(
      double maxRange, int mapSizePixels, double fontScale) {
    return minorIncrements[suggestMajorMinorCategory(maxRange, mapSizePixels, fontScale)];
  }

  /**
   * This uses SgtMap to plot data on a map. Strings should be "" if not needed. This is not static
   * because it uses boundary.
   *
   * @param transparent if true, just the data is drawn: the graph fills the baseULX/YPixel and
   *     imageWidth/HeightPixels area, no legend or axis labels/ticks/lines/titles will be drawn,
   *     and no rivers, lakes, coastlines, boundaries will be drawn. The image's background and
   *     graph color will not be changed (or actively drawn).
   * @param legendPosition one of SgtUtil.LEGEND_RIGHT (not currently supported),
   *     SgtUtil.LEGEND_BELOW
   * @param legendTitle1 the first line of the legend (or both null for no legendTitle)
   * @param legendTitle2 the second line of the legend (or both null for no legendTitle)
   * @param imageDir the directory with the logo file
   * @param logoImageFile the logo image file in the imageDir (should be square image) (currently,
   *     must be png, gif, jpg, or bmp) (currently noaa-simple-40.gif for lowRes), or null for none.
   * @param minX the min lon value on the map and appropriate for the data; must be valid
   * @param maxX the max lon value on the map and appropriate for the data; must be valid
   * @param minY the min lat value on the map and appropriate for the data; must be valid
   * @param maxY the max lat value on the map and appropriate for the data; must be valid
   * @param plotGridData is true if the grid dataset should be plotted (if false, other gridXxx
   *     parameters are ignored)
   * @param gridGrid the data to be plotted as a colored surface. It may span a larger area than the
   *     desired map.
   * @param gridScaleFactor is a scale factor to be applied to the data (use "1" if none)
   * @param gridAltScaleFactor is a scale factor to be applied to the data (use "1" if none)
   * @param gridAltOffset is a scale factor to be added to the data (use "0" if none)
   * @param gridPaletteFileName is the complete name of the palette file to be used
   * @param gridBoldTitle
   * @param gridTitle2
   * @param gridTitle3
   * @param gridTitle4
   * @param drawLakesAndRivers one of the LAKES_AND_RIVERS constants from above. But even if true,
   *     they are never drawn if resolution = 'c'
   * @param plotContourData is true if the contour dataset should be plotted (if false, other
   *     contour parameters are ignored)
   * @param contourGrid the data for the contour lines. It may span a larger area than the desired
   *     map. Even if gridGrid and contourGrid are the same data, use different objects to they
   *     aren't scaled/offset twice.
   * @param contourScaleFactor is a scale factor to be applied (use "1" if none)
   * @param contourAltScaleFactor is a scale factor to be applied to the data (use "1" if none)
   * @param contourAltOffset is a scale factor to be added to the data (use "0" if none)
   * @param contourDrawLinesAt is a single value or a comma-separated list of values at which
   *     contour lines should be drawn param contourPaletteFileName is the complete name of the
   *     palette file to be used
   * @param contourColor is an int with the rgb color value for the contour lines
   * @param contourBoldTitle
   * @param contourUnits
   * @param contourTitle2
   * @param contourDate
   * @param contourCourtesy
   * @param graphDataLayers an ArrayList of GraphDataLayers with the data to be plotted.
   * @param g2 the graphics2D object to be used (the image background color should already have been
   *     drawn)
   * @param baseULXPixel defines area to be used, in pixels
   * @param baseULYPixel defines area to be used, in pixels
   * @param imageWidthPixels defines area to be used, in pixels
   * @param imageHeightPixels defines area to be used, in pixels
   * @param boundaryResAdjust 0=noAdjust; -1,-2,...=higherRes; 1,2,...=lowerRes (e.g., for making
   *     .pdf)
   * @param fontScale relative to 1=normalHeight
   * @return ArrayList with info about where the GraphDataLayer markers were plotted (for generating
   *     the user map on the image: 0=IntArray minX, 1=IntArray maxX, 2=IntArray minY, 3=IntArray
   *     maxY, 4=IntArray rowNumber 5=IntArray whichPointScreen(0,1,2,...)), pixel location of graph
   *     6=IntArray originX,endX,originY,endY, XY double MinMax graph 7=DoubleArray
   *     originX,endX,originY,endY. For 0..5, if no graphDataLayers or no visible stations, these
   *     will exist but have size()=0.
   * @throws Exception
   */
  @SuppressWarnings("ReferenceEquality") // below gridGrid == contourGrid
  public static List<PrimitiveArray> makeMap(
      boolean transparent,
      int legendPosition,
      String legendTitle1,
      String legendTitle2,
      String imageDir,
      String logoImageFile,
      double minX,
      double maxX,
      double minY,
      double maxY,
      String drawLandMask,
      boolean plotGridData,
      Grid gridGrid,
      double gridScaleFactor,
      double gridAltScaleFactor,
      double gridAltOffset,
      String gridPaletteFileName,
      String gridBoldTitle,
      String gridTitle2,
      String gridTitle3,
      String gridTitle4,
      int drawLakesAndRivers,
      boolean plotContourData,
      Grid contourGrid,
      double contourScaleFactor,
      double contourAltScaleFactor,
      double contourAltOffset,
      String contourDrawLinesAt, // contourPaletteFileName,
      Color contourColor,
      String contourBoldTitle,
      String contourUnits,
      String contourTitle2,
      String contourDate,
      String contourCourtesy,
      List<GraphDataLayer> graphDataLayers,
      Graphics2D g2,
      int baseULXPixel,
      int baseULYPixel,
      int imageWidthPixels,
      int imageHeightPixels,
      int boundaryResAdjust,
      double fontScale
      // , String customFileName
      ) throws Exception {

    // Coordinates in SGT:
    // * JPane - uses "device" coordinates (e.g., pixels, ints, 0,0 at upper left).
    // * Layer - same size as JPane, uses "physical" coordinates (doubles, 0,0 at lower left).
    //    It can be in pixels (but with 0,0 at lower left).
    // * Graph - uses "user" coordinates (e.g., lat and lon),
    //   but x/yPhysRange maps user coordinates to JPane/device coordinates.

    // for testing
    // g2.setColor(Color.red);
    // g2.drawRect(0, 0, imageWidthPixels-1, imageHeightPixels-1);

    if (legendTitle1 == null) legendTitle1 = "";
    if (legendTitle2 == null) legendTitle2 = "";

    // set the clip region
    g2.setClip(baseULXPixel, baseULYPixel, imageWidthPixels, imageHeightPixels);
    {
      if (reallyVerbose) String2.log("\n{{ SgtMap.makeMap "); // + Math2.memoryString());
      long startTime = System.currentTimeMillis();
      long time = System.currentTimeMillis();

      // We want == here not object.equals.
      if (contourGrid != null && gridGrid == contourGrid)
        Test.error(String2.ERROR + " in SgtMap.makeMap: gridGrid == contourGrid!");
      if (!Double.isFinite(minX))
        throw new SimpleException(String2.ERROR + " when making map: minLon wasn't set.");
      if (!Double.isFinite(maxX))
        throw new SimpleException(String2.ERROR + " when making map: maxLon wasn't set.");
      if (!Double.isFinite(minY))
        throw new SimpleException(String2.ERROR + " when making map: minLat wasn't set.");
      if (!Double.isFinite(maxY))
        throw new SimpleException(String2.ERROR + " when making map: maxLat wasn't set.");
      if (reallyVerbose)
        String2.log("  minX=" + minX + " maxX=" + maxX + " minY=" + minY + " maxY=" + maxY);

      double axisLabelHeight = fontScale * defaultAxisLabelHeight;
      double labelHeight =
          Math.max(1, fontScale) * defaultLabelHeight; // never smaller than default

      // figure out the params needed to make the map
      if (minX > maxX) {
        double d = minX;
        minX = maxX;
        maxX = d;
      }
      if (minY > maxY) {
        double d = minY;
        minY = maxY;
        maxY = d;
      }
      double xRange = maxX - minX;
      double yRange = maxY - minY;
      double maxRange = Math.max(xRange, yRange);

      double majorIncrement = suggestMajorIncrement(maxRange, imageWidthPixels - 30, fontScale);
      double minorIncrement = suggestMinorIncrement(maxRange, imageWidthPixels - 30, fontScale);
      double vecIncrement = suggestVectorIncrement(maxRange, imageWidthPixels - 30, fontScale);
      int boundaryResolution =
          suggestBoundaryResolution(maxRange, imageWidthPixels - 30, boundaryResAdjust);
      if (reallyVerbose) String2.log("  boundaryResolution=" + boundaryResolution);

      // define sizes
      double dpi = 100; // dots per inch
      double imageWidthInches = imageWidthPixels / dpi;
      double imageHeightInches = imageHeightPixels / dpi;
      double betweenGraphAndColorBar = fontScale * .25;
      double betweenColorBarAndLegend = fontScale * .1;
      if (imageWidthPixels < 300) {
        betweenGraphAndColorBar /= 4;
        betweenColorBarAndLegend /= 4;
      }
      int labelHeightPixels = Math2.roundToInt(labelHeight * dpi);

      // set legend location and size (in pixels)   for LEGEND_RIGHT
      // standard length of vector (and other samples) in user units (e.g., inches)
      double legendSampleSizeInches =
          0.22; // Don't change this (unless make other changes re vector length on graph)
      int legendSampleSize = Math2.roundToInt(legendSampleSizeInches * dpi);
      int legendBoxWidth = Math2.roundToInt(fontScale * 1.4 * dpi); // 1.4inches
      int legendBoxHeight = imageHeightPixels;
      int legendBoxULX = baseULXPixel + imageWidthPixels - legendBoxWidth;
      int legendInsideBorder = Math2.roundToInt(fontScale * 0.1 * dpi);
      int legendTextX = legendBoxULX + legendBoxWidth / 2; // centerX

      // set colorBarBox location and size (in pixels)
      int colorBarBoxWidth = (int) (fontScale * 1.0 * dpi); // size based on longest title|units
      int legendBoxULY = baseULYPixel;
      int maxCharsPerLine =
          SgtUtil.maxCharsPerLine(
              legendBoxWidth - (legendSampleSize + 3 * legendInsideBorder), fontScale);
      int maxBoldCharsPerLine = SgtUtil.maxBoldCharsPerLine(maxCharsPerLine);

      // deal with LEGEND_BELOW   (colorBar drawn inside legendBox)
      StringArray shortBoldLines = null,
          shortLines = null,
          contourShortBoldLines = null,
          contourShortLines = null;
      if (legendPosition == SgtUtil.LEGEND_BELOW) {
        maxCharsPerLine =
            SgtUtil.maxCharsPerLine(
                imageWidthPixels - (legendSampleSize + 3 * legendInsideBorder), fontScale);
        maxBoldCharsPerLine = SgtUtil.maxBoldCharsPerLine(maxCharsPerLine);

        double legendLineCount =
            String2.isSomething(legendTitle1 + legendTitle2)
                ? 1
                : -1; // for legend title   //???needs adjustment for larger font size

        if (plotGridData && gridBoldTitle != null) {
          shortBoldLines = SgtUtil.makeShortLines(maxBoldCharsPerLine, gridBoldTitle, null, null);
          shortLines = SgtUtil.makeShortLines(maxCharsPerLine, gridTitle2, gridTitle3, gridTitle4);
          legendLineCount += 5; // 4 for colorbar, 1 for gap
          legendLineCount += shortBoldLines.size();
          legendLineCount += shortLines.size();
        }
        if (plotContourData && contourBoldTitle != null) {
          contourShortBoldLines =
              SgtUtil.makeShortLines(maxBoldCharsPerLine, contourBoldTitle, null, null);
          contourShortLines =
              SgtUtil.makeShortLines(
                  maxCharsPerLine,
                  SgtUtil.getNewTitle2(contourUnits, contourDate, contourTitle2),
                  contourCourtesy,
                  "");
          legendLineCount += 1; // 1 for gap
          legendLineCount += contourShortBoldLines.size();
          legendLineCount += contourShortLines.size();
        }

        for (GraphDataLayer graphDataLayer : graphDataLayers)
          legendLineCount += graphDataLayer.legendLineCount(maxCharsPerLine);
        legendBoxWidth = imageWidthPixels;
        legendBoxHeight = (int) (legendLineCount * labelHeightPixels) + 2 * legendInsideBorder;
        legendBoxULX = baseULXPixel;
        legendBoxULY = baseULYPixel + imageHeightPixels - legendBoxHeight;
        legendTextX = legendBoxULX + legendSampleSize + 2 * legendInsideBorder; // leftX
      }

      // so stuff to right of graph is
      //  betweenGraphAndColorBar + colorBarBoxWidth + betweenColorBarAndLegend + legendBoxWidth
      // currently:      .25      +      0.9         +          .1              +      1.4 = 2.65

      // determine appropriate axis lengths to best fill available space
      // note  graphHeight/yRange = graphWidth/xRange
      // Standard: for US+Mex, assuming imageHeightInches = 4"
      //      (4-.25-.2)/28°     = graphWidth/30°  -> graphWidth = 3.80"
      //   so imageWidthInches should be 0.25 + 3.80 + 2.65" (for stuff at right) = 6.7"
      // Small: for US+Mex, assuming imageHeightInches = 2.5"
      //      (2.5-.25-.2)/28°     = graphWidth/30°  -> graphWidth = 2.20"
      //   so imageWidthInches should be 0.25 + 2.20 + 2.65" (for stuff at right) = 5.10"
      // Large: for US+Mex, assuming imageHeightInches = 7"
      //      (7-.25-.2)/28°     = graphWidth/30°  -> graphWidth = 7.02"
      //   so imageWidthInches should be 0.25 + 7.02 + 2.65" (for stuff at right) = 9.92"
      double graphULX = fontScale * 0.25; // relative to baseULXYPixel
      double graphULY = fontScale * 0.2;
      if (imageWidthPixels < 300) graphULY /= 2;
      double graphBottomY = fontScale * 0.25;
      double graphWidth =
          imageWidthInches
              - graphULX
              - legendBoxWidth / dpi
              - betweenGraphAndColorBar
              - (plotGridData ? betweenColorBarAndLegend + colorBarBoxWidth / dpi : 0);
      double graphHeight = imageHeightInches - graphBottomY - graphULY;
      if (legendPosition == SgtUtil.LEGEND_BELOW) {
        graphWidth = imageWidthInches - graphULX - betweenGraphAndColorBar;
        graphHeight = imageHeightInches - graphBottomY - graphULY - legendBoxHeight / dpi;
      }

      double tempXScale = graphWidth / xRange;
      double tempYScale = graphHeight / yRange;
      double graphScale = Math.min(tempXScale, tempYScale);
      if (tempXScale < tempYScale) {
        // adjust y axis
        double newGraphHeight = graphScale * yRange;
        double diff = graphHeight - newGraphHeight;
        if (legendPosition == SgtUtil.LEGEND_BELOW) {
          graphBottomY += diff;
          legendBoxULY -= Math2.roundToInt(diff * dpi);
        } else {
          graphULY += diff / 2;
          graphBottomY += diff / 2;
        }
        graphHeight = newGraphHeight;
      } else {
        // adjust x axis
        double newGraphWidth = graphScale * xRange;
        double diff = graphWidth - newGraphWidth;
        if (legendPosition == SgtUtil.LEGEND_BELOW) graphULX += diff / 2;
        else graphULX += diff;
        graphWidth = newGraphWidth;
      }
      int graphWidthPixels = Math2.roundToInt(graphWidth * dpi);
      int graphHeightPixels = Math2.roundToInt(graphHeight * dpi);
      if (reallyVerbose)
        String2.log(
            "  graphULX="
                + String2.genEFormat10(graphULX)
                + " ULY="
                + String2.genEFormat10(graphULY)
                + " width="
                + String2.genEFormat10(graphWidth)
                + " height="
                + String2.genEFormat10(graphHeight)
                + "\n  bottomY="
                + String2.genEFormat10(graphBottomY)
                + " widthPixels="
                + graphWidthPixels
                + " heightPixels="
                + graphHeightPixels);

      // but if transparent, reset the graph position and ignore legend position
      if (transparent) {
        graphULX = 0; // relative to baseULXYPixel
        graphULY = 0;
        graphBottomY = 0;
        graphWidth = imageWidthInches;
        graphHeight = imageHeightInches;
      }

      // set legendTextY   after graph size and position known
      int legendTextY = legendBoxULY + legendInsideBorder + labelHeightPixels;
      if (reallyVerbose)
        String2.log(
            "  baseULXPixel="
                + baseULXPixel
                + " baseULYPixel="
                + baseULYPixel
                + "\n  legendBoxWidth="
                + legendBoxWidth
                + " boxHeight="
                + legendBoxHeight
                + " boxULX="
                + legendBoxULX
                + " boxULY="
                + legendBoxULY
                + " textX="
                + legendTextX
                + " textY="
                + legendTextY
                + "\n  insideBorder="
                + legendInsideBorder
                + " labelHeightPixels="
                + labelHeightPixels
                + " nGraphDataLayers="
                + graphDataLayers.size());

      // create the label font
      Font labelFont = new Font(fontFamily, Font.PLAIN, 10); // Font.ITALIC

      // drawHtmlText needs non-text antialiasing ON
      // but if transparent, turn antialiasing OFF (fuzzy pixels make a halo around things)
      Object originalAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      g2.setRenderingHint(
          RenderingHints.KEY_ANTIALIASING,
          transparent ? RenderingHints.VALUE_ANTIALIAS_OFF : RenderingHints.VALUE_ANTIALIAS_ON);

      // draw legend basics
      if (!transparent) {
        // box for legend
        g2.setColor(new Color(0xFFFFCC));
        g2.fillRect(legendBoxULX, legendBoxULY, legendBoxWidth - 1, legendBoxHeight - 1);
        g2.setColor(Color.black);
        g2.drawRect(legendBoxULX, legendBoxULY, legendBoxWidth - 1, legendBoxHeight - 1);

        // legend titles
        if (String2.isSomething(legendTitle1 + legendTitle2)) {
          if (legendPosition == SgtUtil.LEGEND_BELOW) {
            // draw LEGEND_BELOW
            legendTextY =
                SgtUtil.drawHtmlText(
                    g2,
                    legendTextX,
                    legendTextY,
                    0,
                    fontFamily,
                    labelHeightPixels * 3 / 2,
                    false,
                    "<strong><color=#2600aa>"
                        + SgtUtil.encodeAsHtml(legendTitle1 + " " + legendTitle2)
                        + "</color></strong>");
            legendTextY += labelHeightPixels / 2;
          } else {
            // draw LEGEND_RIGHT
            int tx = legendBoxULX + legendInsideBorder;
            if (legendTitle1.length() > 0)
              legendTextY =
                  SgtUtil.drawHtmlText(
                      g2,
                      tx,
                      legendTextY,
                      0,
                      fontFamily,
                      labelHeightPixels * 5 / 4,
                      false,
                      "<strong><color=#2600aa>"
                          + SgtUtil.encodeAsHtml(legendTitle1)
                          + "</color></strong>");
            if (legendTitle2.length() > 0)
              legendTextY =
                  SgtUtil.drawHtmlText(
                      g2,
                      tx,
                      legendTextY,
                      0,
                      fontFamily,
                      labelHeightPixels * 5 / 4,
                      false,
                      "<strong><color=#2600aa>"
                          + SgtUtil.encodeAsHtml(legendTitle2)
                          + "</color></strong>");
            legendTextY += labelHeightPixels * 3 / 2;
          }

          // draw the logo
          if (logoImageFile != null && File2.isFile(imageDir + logoImageFile)) {
            time = System.currentTimeMillis();
            BufferedImage bi2 = ImageIO.read(new File(imageDir + logoImageFile));

            // g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            //                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            //                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            //                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            // draw LEGEND_RIGHT
            int ulx = baseULXPixel + (int) ((imageWidthInches - fontScale * 0.45) * dpi);
            int uly = baseULYPixel + (int) (fontScale * 0.05 * dpi);
            int tSize = (int) (fontScale * 40);
            if (legendPosition == SgtUtil.LEGEND_BELOW) {
              // draw LEGEND_BELOW
              ulx = legendBoxULX + legendSampleSize / 2;
              uly = legendBoxULY + legendInsideBorder / 2;
              tSize = (int) (fontScale * 20);
            }
            g2.drawImage(bi2, ulx, uly, tSize, tSize, null); // null=ImageObserver
            if (reallyVerbose)
              String2.log("  draw logo time=" + (System.currentTimeMillis() - time) + "ms");
          }
        }
      }

      // create the pane
      JPane jPane =
          new JPane(
              "",
              new java.awt.Dimension(
                  baseULXPixel + imageWidthPixels, baseULYPixel + imageHeightPixels));
      jPane.setLayout(new StackedLayout());

      // create the common graph parts
      // graph's physical location (start, end, delta); delta is ignored
      Range2D xPhysRange =
          new Range2D(baseULXPixel / dpi + graphULX, baseULXPixel / dpi + graphULX + graphWidth, 1);
      Range2D yPhysRange =
          new Range2D(
              (transparent ? 0 : legendPosition == SgtUtil.LEGEND_BELOW ? legendBoxHeight / dpi : 0)
                  + graphBottomY,
              imageHeightInches - graphULY,
              1);

      // create the results arrays
      IntArray resultMinX = new IntArray();
      IntArray resultMaxX = new IntArray();
      IntArray resultMinY = new IntArray();
      IntArray resultMaxY = new IntArray();
      IntArray resultRowNumber = new IntArray();
      IntArray resultPointScreen = new IntArray();
      IntArray graphIntWESN = new IntArray();
      DoubleArray graphDoubleWESN = new DoubleArray();
      ArrayList<PrimitiveArray> results = new ArrayList<>();
      results.add(resultMinX);
      results.add(resultMaxX);
      results.add(resultMinY);
      results.add(resultMaxY);
      results.add(resultRowNumber);
      results.add(resultPointScreen);
      results.add(graphIntWESN);
      results.add(graphDoubleWESN);
      if (transparent) {
        graphIntWESN.add(0); // originX
        graphIntWESN.add(imageWidthPixels - 1); // farX
        graphIntWESN.add(imageHeightPixels - 1); // originY
        graphIntWESN.add(0); // farY
      } else {
        graphIntWESN.add(Math2.roundToInt(xPhysRange.start * dpi)); // originX
        graphIntWESN.add(Math2.roundToInt(xPhysRange.end * dpi)); // farX
        graphIntWESN.add(Math2.roundToInt((imageHeightInches - yPhysRange.start) * dpi)); // originY
        graphIntWESN.add(Math2.roundToInt((imageHeightInches - yPhysRange.end) * dpi)); // farY
      }
      if (reallyVerbose) String2.log("  graphIntWESN=" + graphIntWESN.toString());
      graphDoubleWESN.add(minX);
      graphDoubleWESN.add(maxX);
      graphDoubleWESN.add(minY);
      graphDoubleWESN.add(maxY);

      // graph's x axis range in degrees
      Range2D xUserRange = new Range2D(minX, maxX, majorIncrement);
      Range2D yUserRange = new Range2D(minY, maxY, majorIncrement);
      gov.noaa.pmel.sgt.LinearTransform xt =
          new gov.noaa.pmel.sgt.LinearTransform(xPhysRange, xUserRange);
      gov.noaa.pmel.sgt.LinearTransform yt =
          new gov.noaa.pmel.sgt.LinearTransform(yPhysRange, yUserRange);
      Point2D.Double origin = new Point2D.Double(xUserRange.start, yUserRange.start);
      Dimension2D layerDimension2D =
          new Dimension2D(
              baseULXPixel / dpi + imageWidthInches, baseULYPixel / dpi + imageHeightInches);
      StringArray layerNames = new StringArray();
      ArrayList<VectorPointsRenderer> vectorPointsRenderers = new ArrayList<>();

      if (drawLakesAndRivers < NO_LAKES_AND_RIVERS) drawLakesAndRivers = NO_LAKES_AND_RIVERS;
      if (drawLakesAndRivers > FILL_LAKES_AND_RIVERS) drawLakesAndRivers = FILL_LAKES_AND_RIVERS;

      // colorMap outside loop since timing info is gathered below
      CompoundColorMap colorMap = null;
      Exception thrownException = null;
      try {

        if ("under".equals(drawLandMask) && !transparent) {
          // *** draw land as base
          {
            CartesianGraph graph = new CartesianGraph("", xt, yt);
            Layer layer = new Layer("landunder", layerDimension2D);
            layerNames.add(layer.getId());
            jPane.add(layer); // calls layer.setPane(this);
            layer.setGraph(graph); // calls graph.setLayer(this);

            // assign the data   (PathCartesionRenderer always clips by itself)
            graph.setRenderer(
                new PathCartesianRenderer(
                    graph,
                    GSHHS.getGeneralPath(
                        GSHHS.RESOLUTIONS.charAt(boundaryResolution),
                        1, // just get land info
                        minX,
                        maxX,
                        minY,
                        maxY,
                        true),
                    1e-6,
                    landColor, // fillColor
                    gridBoldTitle != null && gridBoldTitle.indexOf(BATHYMETRY_BOLD_TITLE) >= 0
                        ? landMaskStrokeColor
                        : landColor)); // strokeColor    //2009-10-29 landColor was null
          }

          // *** draw lakes as base
          if (drawLakesAndRivers != NO_LAKES_AND_RIVERS && boundaryResolution < cRes) {
            CartesianGraph graph = new CartesianGraph("", xt, yt);
            Layer layer = new Layer("lakesunder", layerDimension2D);
            layerNames.add(layer.getId());
            jPane.add(layer); // calls layer.setPane(this);
            layer.setGraph(graph); // calls graph.setLayer(this);

            // assign the data   (PathCartesionRenderer always clips by itself)
            graph.setRenderer(
                new PathCartesianRenderer(
                    graph,
                    GSHHS.getGeneralPath(
                        GSHHS.RESOLUTIONS.charAt(boundaryResolution),
                        2, // just get lakes info
                        minX,
                        maxX,
                        minY,
                        maxY,
                        true),
                    1e-6,
                    drawLakesAndRivers == FILL_LAKES_AND_RIVERS ? lakesColor : null, // fillColor
                    lakesColor)); // strokeColor
          }
        }

        // *** create a layer with the GRID DATA graph
        if (plotGridData) {
          // String2.log("NO DATA=false; griddata.");
          colorMap = new CompoundColorMap(gridPaletteFileName);
          CartesianGraph graph = new CartesianGraph("", xt, yt);
          Layer layer = new Layer("grid", layerDimension2D);
          layerNames.add(layer.getId());
          jPane.add(layer); // calls layer.setPane(this);
          layer.setGraph(graph); // calls graph.setLayer(this);
          graph.setClip(
              xUserRange.start, xUserRange.end,
              yUserRange.start, yUserRange.end);
          graph.setClipping(true);

          // get the Grid
          DataHelper.scale(gridGrid.data, gridScaleFactor * gridAltScaleFactor, gridAltOffset);
          SimpleGrid simpleGrid = new SimpleGrid(gridGrid.data, gridGrid.lon, gridGrid.lat);

          // assign the data
          graph.setData(simpleGrid, new GridAttribute(GridAttribute.RASTER, colorMap));

          if (gridBoldTitle == null) {
          } else if (legendPosition == SgtUtil.LEGEND_BELOW) {
            // draw LEGEND_BELOW
            // add a horizontal colorBar
            legendTextY += labelHeightPixels;
            CompoundColorMapLayerChild ccmLayerChild = new CompoundColorMapLayerChild("", colorMap);
            ccmLayerChild.setRectangle( // leftX,upperY(when rotated),width,height
                layer.getXDtoP(legendTextX),
                layer.getYDtoP(legendTextY),
                imageWidthInches
                    - (2 * legendInsideBorder + legendSampleSize) / dpi
                    - betweenGraphAndColorBar,
                fontScale * 0.15);
            ccmLayerChild.setLabelFont(labelFont);
            ccmLayerChild.setLabelHeightP(axisLabelHeight);
            ccmLayerChild.setTicLength(fontScale * 0.02);
            layer.addChild(ccmLayerChild);
            legendTextY += 3 * labelHeightPixels;

            // add legend text
            legendTextY =
                SgtUtil.belowLegendText(
                    g2,
                    legendTextX,
                    legendTextY,
                    fontFamily,
                    labelHeightPixels,
                    shortBoldLines,
                    shortLines);
          } else {
            /*
            //draw LEGEND_RIGHT    //NO LONGER UP-TO-DATE
            //box for colorBar
            g2.setColor(new Color(0xFFFFCC));
            g2.fillRect(colorBarBoxLeftX, baseULYPixel,
                colorBarBoxWidth - 1, imageHeightPixels - 1);
            g2.setColor(Color.black);
            g2.drawRect(colorBarBoxLeftX, baseULYPixel,
                colorBarBoxWidth - 1, imageHeightPixels - 1);

            //add a vertical colorBar
            CompoundColorMapLayerChild ccmLayerChild =
                new CompoundColorMapLayerChild("", colorMap);
            int bottomStuff = legendInsideBorder + 3 * labelHeightPixels;
            ccmLayerChild.setRectangle( //leftX,lowerY,width,height
                layer.getXDtoP(colorBarBoxLeftX + legendInsideBorder),
                layer.getYDtoP(baseULYPixel + imageHeightPixels - bottomStuff),
                fontScale * 0.2, //inches
                (imageHeightPixels - legendInsideBorder - labelHeightPixels/2 - bottomStuff)/dpi);
            ccmLayerChild.setLabelFont(labelFont);
            ccmLayerChild.setLabelHeightP(axisLabelHeight);
            ccmLayerChild.setTicLength(fontScale * 0.02);
            layer.addChild(ccmLayerChild);

            //add text in the colorBarBox
            if (verbose) String2.log("  baseULY=" + baseULYPixel +
                " imageHeightPixels=" + imageHeightPixels +
                " inside=" + legendInsideBorder +
                " labelHeightPixels=" + labelHeightPixels);
            int ty = baseULYPixel + imageHeightPixels - legendInsideBorder - labelHeightPixels;
            ty = SgtUtil.drawHtmlText(g2, colorBarBoxLeftX + colorBarBoxWidth / 2,
                ty, 1, fontFamily, labelHeightPixels, false,
                "<strong>" + SgtUtil.encodeAsHtml(gridBoldTitle) + "</strong>");
            SgtUtil.drawHtmlText(g2, colorBarBoxLeftX + colorBarBoxWidth / 2,
                ty, 1, fontFamily, labelHeightPixels, false, SgtUtil.encodeAsHtml(gridUnits));

            //add legend text
            legendTextY = SgtUtil.drawHtmlText(g2, legendTextX, legendTextY,
                1, fontFamily, labelHeightPixels, false,
                "<strong>" + SgtUtil.encodeAsHtml(gridBoldTitle) + "</strong>");
            legendTextY = SgtUtil.drawHtmlText(g2, legendTextX, legendTextY,
                1, fontFamily, labelHeightPixels, false, SgtUtil.encodeAsHtml(gridUnits));
            legendTextY = SgtUtil.drawHtmlText(g2, legendTextX, legendTextY,
                1, fontFamily, labelHeightPixels, false,
                SgtUtil.encodeAsHtml(gridTitle2));
            legendTextY = SgtUtil.drawHtmlText(g2, legendTextX, legendTextY,
                1, fontFamily, labelHeightPixels, false,
                SgtUtil.encodeAsHtml(gridDate));
            legendTextY = SgtUtil.drawHtmlText(g2, legendTextX, legendTextY,
                1, fontFamily, labelHeightPixels, true,
                SgtUtil.encodeAsHtml(gridCourtesy));
            */
          }
        }

        // *** create a layer with the CONTOUR graph
        // String2.log("  before contour: " + Math2.memoryString());
        if (plotContourData) {
          // String2.log("NO DATA=false; contourdata.");
          CartesianGraph graph = new CartesianGraph("", xt, yt);
          Layer layer = new Layer("contour", layerDimension2D);
          layerNames.add(layer.getId());
          jPane.add(layer); // calls layer.setPane(this);
          layer.setGraph(graph); // calls graph.setLayer(this);
          graph.setClip(
              xUserRange.start, xUserRange.end,
              yUserRange.start, yUserRange.end);
          graph.setClipping(true);

          // get the Grid
          DataHelper.scale(
              contourGrid.data, contourScaleFactor * contourAltScaleFactor, contourAltOffset);
          SimpleGrid simpleGrid =
              new SimpleGrid(contourGrid.data, contourGrid.lon, contourGrid.lat);
          contourGrid.calculateStats(); // so grid.minData maxData is correct
          double gridMinData = contourGrid.minData;
          double gridMaxData = contourGrid.maxData;

          // assign the data
          double[] levels =
              Grid.generateContourLevels(contourDrawLinesAt, gridMinData, gridMaxData);
          if (reallyVerbose)
            String2.log(
                "  contour asf="
                    + contourAltScaleFactor
                    + " ao="
                    + contourAltOffset
                    + " linesAt="
                    + contourDrawLinesAt
                    + " levels="
                    + String2.toCSSVString(levels)
                    + " minData="
                    + String2.genEFormat6(gridMinData)
                    + " maxData="
                    + String2.genEFormat10(gridMaxData));
          DecimalFormat format = new DecimalFormat("#0.######");
          ContourLevels contourLevels = new ContourLevels();
          for (double level : levels) {
            ContourLineAttribute contourLineAttribute = new ContourLineAttribute();
            contourLineAttribute.setColor(contourColor);
            contourLineAttribute.setLabelColor(contourColor);
            contourLineAttribute.setLabelHeightP(fontScale * 0.15);
            contourLineAttribute.setLabelFormat("%g"); // this seems to be active
            contourLineAttribute.setLabelText(format.format(level)); // this seems to be ignored
            contourLevels.addLevel(level, contourLineAttribute);
          }
          graph.setData(simpleGrid, new GridAttribute(contourLevels));
          if (reallyVerbose) String2.log("  contour levels = " + String2.toCSSVString(levels));

          // add legend text
          if (legendPosition == SgtUtil.LEGEND_BELOW) {
            // draw LEGEND_BELOW
            g2.setColor(contourColor);
            g2.drawLine(
                legendTextX - legendSampleSize - legendInsideBorder,
                legendTextY - labelHeightPixels / 2,
                legendTextX - legendInsideBorder,
                legendTextY - labelHeightPixels / 2);

            // add legend text
            legendTextY =
                SgtUtil.belowLegendText(
                    g2,
                    legendTextX,
                    legendTextY,
                    fontFamily,
                    labelHeightPixels,
                    contourShortBoldLines,
                    contourShortLines);
          } else {
            // draw LEGEND_RIGHT
            g2.setColor(contourColor);
            g2.drawLine(
                legendTextX - legendSampleSize / 2,
                legendTextY - labelHeightPixels * 7 / 8,
                legendTextX + legendSampleSize / 2,
                legendTextY - labelHeightPixels * 7 / 8);

            legendTextY += labelHeightPixels / 2; // for demo line
            legendTextY =
                SgtUtil.drawHtmlText(
                    g2,
                    legendTextX,
                    legendTextY,
                    1,
                    fontFamily,
                    labelHeightPixels,
                    false,
                    "<strong>" + SgtUtil.encodeAsHtml(contourBoldTitle) + "</strong>");
            legendTextY =
                SgtUtil.drawHtmlText(
                    g2,
                    legendTextX,
                    legendTextY,
                    1,
                    fontFamily,
                    labelHeightPixels,
                    false,
                    SgtUtil.encodeAsHtml(contourUnits));
            legendTextY =
                SgtUtil.drawHtmlText(
                    g2,
                    legendTextX,
                    legendTextY,
                    1,
                    fontFamily,
                    labelHeightPixels,
                    false,
                    SgtUtil.encodeAsHtml(contourTitle2));
            legendTextY =
                SgtUtil.drawHtmlText(
                    g2,
                    legendTextX,
                    legendTextY,
                    1,
                    fontFamily,
                    labelHeightPixels,
                    false,
                    SgtUtil.encodeAsHtml(contourDate));
            legendTextY =
                SgtUtil.drawHtmlText(
                    g2,
                    legendTextX,
                    legendTextY,
                    1,
                    fontFamily,
                    labelHeightPixels,
                    true,
                    SgtUtil.encodeAsHtml(contourCourtesy));
          }
        }

        // *** draw the landmask or coastline
        // Note that drawing landmask here obscures any grid/bath/contour data
        // over land (e.g., lakes).
        // [For CWBrowsers: this was Dave's request  2006-03-29.]
        if (!transparent) {
          CartesianGraph graph = new CartesianGraph("", xt, yt);
          Layer layer = new Layer("landmask", layerDimension2D);
          layerNames.add(layer.getId());
          jPane.add(layer); // calls layer.setPane(this);
          layer.setGraph(graph); // calls graph.setLayer(this);

          // assign the data   (PathCartesionRenderer always clips by itself)
          graph.setRenderer(
              new PathCartesianRenderer(
                  graph,
                  GSHHS.getGeneralPath(
                      GSHHS.RESOLUTIONS.charAt(boundaryResolution),
                      1, // just get land info
                      minX,
                      maxX,
                      minY,
                      maxY,
                      true),
                  1e-6,
                  "over".equals(drawLandMask) ? landColor : null, // fillColor
                  "off".equals(drawLandMask)
                      ? null
                      : "over".equals(drawLandMask)
                          ? // strokeColor
                          (gridBoldTitle != null
                                  && gridBoldTitle.indexOf(BATHYMETRY_BOLD_TITLE) >= 0
                              ? landMaskStrokeColor
                              : landColor)
                          : landMaskStrokeColor)); // under or outline
        }

        // draw lakes
        if (drawLakesAndRivers != NO_LAKES_AND_RIVERS
            && boundaryResolution < cRes
            && !transparent) {
          CartesianGraph graph = new CartesianGraph("", xt, yt);
          Layer layer = new Layer("lakes", layerDimension2D);
          layerNames.add(layer.getId());
          jPane.add(layer); // calls layer.setPane(this);
          layer.setGraph(graph); // calls graph.setLayer(this);

          // assign the data   (PathCartesionRenderer always clips by itself)
          graph.setRenderer(
              new PathCartesianRenderer(
                  graph,
                  GSHHS.getGeneralPath(
                      GSHHS.RESOLUTIONS.charAt(boundaryResolution),
                      2, // lakes
                      minX,
                      maxX,
                      minY,
                      maxY,
                      true),
                  1e-6,
                  drawLakesAndRivers == FILL_LAKES_AND_RIVERS ? lakesColor : null, // fillColor
                  lakesColor)); // strokeColor
        }

        // *** draw rivers  (but don't if plotGridData since easy to confuse data and rivers)
        if (drawLakesAndRivers != NO_LAKES_AND_RIVERS
            && boundaryResolution < cRes
            && !transparent) {
          CartesianGraph graph = new CartesianGraph("", xt, yt);
          Layer layer = new Layer("rivers", layerDimension2D);
          layerNames.add(layer.getId());
          jPane.add(layer); // calls layer.setPane(this);
          layer.setGraph(graph); // calls graph.setLayer(this);
          graph.setClip(
              xUserRange.start, xUserRange.end,
              yUserRange.start, yUserRange.end);
          graph.setClipping(true);

          LineAttribute lineAttribute = new LineAttribute();
          lineAttribute.setColor(riversColor);
          graph.setData(
              rivers.getSgtLine(boundaryResolution, minX, maxX, minY, maxY), lineAttribute);
        }

        // *** draw the StateBOUNDARY
        if (drawPoliticalBoundaries
            && boundaryResolution < cRes
            && !"off".equals(drawLandMask)
            && !transparent) {
          CartesianGraph graph = new CartesianGraph("", xt, yt);
          Layer layer = new Layer("stateBoundary", layerDimension2D);
          layerNames.add(layer.getId());
          jPane.add(layer); // calls layer.setPane(this);
          layer.setGraph(graph); // calls graph.setLayer(this);
          graph.setClip(
              xUserRange.start, xUserRange.end,
              yUserRange.start, yUserRange.end);
          graph.setClipping(true);

          LineAttribute lineAttribute = new LineAttribute();
          lineAttribute.setColor(statesColor);
          graph.setData(
              stateBoundaries.getSgtLine(boundaryResolution, minX, maxX, minY, maxY),
              lineAttribute);
        }

        // *** draw the NationalBOUNDARY
        if (drawPoliticalBoundaries && !"off".equals(drawLandMask) && !transparent) {
          CartesianGraph graph = new CartesianGraph("", xt, yt);
          Layer layer = new Layer("nationalBoundary", layerDimension2D);
          layerNames.add(layer.getId());
          jPane.add(layer); // calls layer.setPane(this);
          layer.setGraph(graph); // calls graph.setLayer(this);
          graph.setClip(
              xUserRange.start, xUserRange.end,
              yUserRange.start, yUserRange.end);
          graph.setClipping(true);

          LineAttribute lineAttribute = new LineAttribute();
          lineAttribute.setColor(nationsColor);
          graph.setData(
              nationalBoundaries.getSgtLine(boundaryResolution, minX, maxX, minY, maxY),
              lineAttribute);
        }

        // draw the point layers
        FilledMarkerRenderer filledMarkerRenderers[] =
            new FilledMarkerRenderer[graphDataLayers.size()]; // a slot for each, even if null
        for (int i = 0; i < graphDataLayers.size(); i++) {
          long tTime = System.currentTimeMillis();

          CartesianGraph graph = new CartesianGraph("", xt, yt);
          Layer layer = new Layer("pointLayer" + i, layerDimension2D);
          layerNames.add(layer.getId());
          jPane.add(layer); // calls layer.setPane(this);
          layer.setGraph(graph); // calls graph.setLayer(this);
          graph.setClip(
              xUserRange.start, xUserRange.end,
              yUserRange.start, yUserRange.end);
          graph.setClipping(true);

          // get the data
          GraphDataLayer gdl = graphDataLayers.get(i);
          // String2.log("  averagedTable=" + averagedTable);
          if (gdl.draw == GraphDataLayer.DRAW_LINES
              || gdl.draw == GraphDataLayer.DRAW_MARKERS
              || gdl.draw == GraphDataLayer.DRAW_MARKERS_AND_LINES) {
            if (gdl.draw == GraphDataLayer.DRAW_LINES) {
              gdl.colorMap = null;
              gdl.markerType = GraphDataLayer.MARKER_TYPE_NONE;
            }
            Table averagedTable = gdl.table;
            if (averagedTable.nRows() > 0) {
              // String2.log("NO DATA=false; markers hava data.");
              filledMarkerRenderers[i] =
                  new FilledMarkerRenderer(
                      graph,
                      gdl.sourceID,
                      averagedTable.getColumn(gdl.v1),
                      averagedTable.getColumn(gdl.v2),
                      averagedTable.getColumn(
                          gdl.v3 >= 0 ? gdl.v3 : gdl.v1), // e.g., if no gdl.colorMap
                      gdl.colorMap,
                      gdl.lineColor,
                      gdl.markerType,
                      Math2.roundToInt(fontScale * gdl.markerSize),
                      gdl.draw == GraphDataLayer.DRAW_LINES
                          || gdl.draw == GraphDataLayer.DRAW_MARKERS_AND_LINES);
              graph.setRenderer(filledMarkerRenderers[i]);
            } else {
              String2.log("  SgtMap: graphDataLayer has 0 rows in gdl.table.");
            }
          } else if (gdl.draw == GraphDataLayer.DRAW_POINT_VECTORS
              || gdl.draw == GraphDataLayer.DRAW_GRID_VECTORS) {

            // get the data columns: x,y,u,v
            PrimitiveArray xColumn, yColumn, uColumn, vColumn;
            try {
              if (gdl.draw == GraphDataLayer.DRAW_POINT_VECTORS) {
                // get the x,y,u,v columns from the ncFile
                Table tTable = gdl.table;
                xColumn = tTable.getColumn(gdl.v1);
                yColumn = tTable.getColumn(gdl.v2);
                uColumn = tTable.getColumn(gdl.v3);
                vColumn = tTable.getColumn(gdl.v4);
              } else { // for gdl.draw == GraphDataLayer.DRAW_GRID_VECTORS
                // get the Grids
                int lonNNeeded = Math.max(1, Math2.roundToInt(xRange / vecIncrement));
                int latNNeeded = Math.max(1, Math2.roundToInt(yRange / vecIncrement));
                if (reallyVerbose)
                  String2.log(
                      "  grid vectors vecIncrement="
                          + vecIncrement
                          + " lonNNeeded="
                          + lonNNeeded
                          + " latNNeeded="
                          + latNNeeded);
                Grid uGrid = gdl.grid1;
                Grid vGrid = gdl.grid2;
                uGrid.makeLonPM180AndSubset(minX, maxX, minY, maxY, lonNNeeded, latNNeeded);
                vGrid.makeLonPM180AndSubset(minX, maxX, minY, maxY, lonNNeeded, latNNeeded);

                // DataHelper.scale(uGrid.data, vectorXScaleFactor, 0);
                // DataHelper.scale(vGrid.data, vectorYScaleFactor, 0);
                Test.ensureEqual(uGrid.lat, vGrid.lat, "uGrid.lat != vGrid");
                Test.ensureEqual(uGrid.lon, vGrid.lon, "uGrid.lon != vGrid");
                int nLon = uGrid.lon.length;
                int nLat = uGrid.lat.length;
                xColumn = new DoubleArray(nLon * nLat, false);
                yColumn = new DoubleArray(nLon * nLat, false);
                uColumn = new DoubleArray(nLon * nLat, false);
                vColumn = new DoubleArray(nLon * nLat, false);
                for (int x = 0; x < nLon; x++) {
                  for (int y = 0; y < nLat; y++) {
                    xColumn.addDouble(uGrid.lon[x]);
                    yColumn.addDouble(uGrid.lat[y]);
                    uColumn.addDouble(uGrid.getData(x, y));
                    vColumn.addDouble(vGrid.getData(x, y));
                  }
                }
              }
            } catch (Exception e) {
              String2.log("Exception caught in SgtMap.makeMap:\n" + MustBe.throwableToString(e));
              xColumn = null;
              return null;
            }

            if (xColumn != null && xColumn.size() > 0) {
              // String2.log("NO DATA=false; vectors hava data.");

              // vectorSize scales values relative to standard length vector,
              // e.g., 10 m s-1 -> 10
              VectorAttribute2 vectorAttribute =
                  new VectorAttribute2(
                      legendSampleSizeInches / gdl.vectorStandard,
                      gdl.colorMap,
                      gdl.lineColor,
                      gdl.sourceID);
              vectorAttribute.setHeadFixedSize(0.05);
              // vectorAttribute.setWidth(0.5f);
              VectorPointsRenderer vectorPointsRenderer =
                  new VectorPointsRenderer(
                      graph,
                      new SGTPointsVector(
                          xColumn.toDoubleArray(),
                          yColumn.toDoubleArray(),
                          uColumn.toDoubleArray(),
                          vColumn.toDoubleArray()),
                      vectorAttribute);
              graph.setRenderer(vectorPointsRenderer);
              vectorPointsRenderers.add(vectorPointsRenderer);
            } else String2.log("  SgtMap: No data for vectors.\nspl=" + gdl.toString());

          } else {
            Test.error(
                String2.ERROR
                    + " in SgtMap.makeMap: Unsupported GraphDataLayer.draw value: "
                    + gdl.draw
                    + "\nspl="
                    + gdl.toString());
          }

          // add legend vector (size not affected by fontScale) and text
          Color legendVectorColor = gdl.lineColor;
          if (!transparent && legendVectorColor == null) {
            Range2D range2D = gdl.colorMap.getRange();
            legendVectorColor = gdl.colorMap.getColor(range2D.end);
          }

          if (gdl.boldTitle == null || transparent) {
            // draw nothing in legend
          } else if (legendPosition == SgtUtil.LEGEND_BELOW) {

            // add a horizontal colorBar
            if (gdl.colorMap instanceof CompoundColorMap) {
              legendTextY += labelHeightPixels;
              CompoundColorMapLayerChild ccmLayerChild =
                  new CompoundColorMapLayerChild("", (CompoundColorMap) gdl.colorMap);
              ccmLayerChild.setRectangle( // leftX,upperY(when rotated),width,height
                  layer.getXDtoP(legendTextX),
                  layer.getYDtoP(legendTextY),
                  imageWidthInches
                      - (2 * legendInsideBorder + legendSampleSize) / dpi
                      - betweenGraphAndColorBar,
                  fontScale * 0.15);
              ccmLayerChild.setLabelFont(labelFont);
              ccmLayerChild.setLabelHeightP(axisLabelHeight);
              ccmLayerChild.setTicLength(fontScale * 0.02);
              layer.addChild(ccmLayerChild);
              legendTextY += 3 * labelHeightPixels;
            }
            if (gdl.draw == GraphDataLayer.DRAW_LINES) {
              int tSize =
                  labelHeightPixels
                      * 3
                      / 5; // nice size; not meant to match GraphDataLayer.markerSize
              int tx = legendTextX - legendInsideBorder - legendSampleSize / 2 - tSize / 2;
              int ty = legendTextY - tSize;
              g2.setColor(gdl.lineColor);
              g2.drawLine(tx - legendSampleSize / 2, ty, tx + legendSampleSize / 2, ty);

            } else if (gdl.draw == GraphDataLayer.DRAW_MARKERS
                || gdl.draw == GraphDataLayer.DRAW_MARKERS_AND_LINES) {
              int tSize =
                  labelHeightPixels
                      * 3
                      / 5; // nice size; not meant to match GraphDataLayer.markerSize
              int tx = legendTextX - legendInsideBorder - legendSampleSize / 2 - tSize / 2;
              int ty = legendTextY - tSize;
              if (gdl.draw == GraphDataLayer.DRAW_MARKERS_AND_LINES) {
                g2.setColor(gdl.lineColor);
                g2.drawLine(tx - legendSampleSize / 2, ty, tx + legendSampleSize / 2, ty);
              }
              SgtGraph.drawMarker(
                  g2,
                  gdl.markerType,
                  gdl.markerSize,
                  tx,
                  ty,
                  gdl.colorMap == null
                      ? gdl.lineColor
                      : gdl.colorMap.getColor(
                          (gdl.colorMap.getRange().start + gdl.colorMap.getRange().end) / 2),
                  gdl.lineColor);

            } else if (gdl.draw == GraphDataLayer.DRAW_POINT_VECTORS
                || gdl.draw == GraphDataLayer.DRAW_GRID_VECTORS) {
              g2.setColor(legendVectorColor);
              int tx = legendTextX - legendInsideBorder - legendSampleSize / 2;
              int ty = legendTextY - labelHeightPixels / 2;
              g2.drawLine(tx - legendSampleSize / 2, ty, tx + legendSampleSize / 2, ty);
              int xPoints[] =
                  new int[] {
                    tx + legendSampleSize / 4, // 1/2 to right - 1/4 for head
                    tx + legendSampleSize / 2,
                    tx + legendSampleSize / 4
                  };
              int yPoints[] = new int[] {ty - legendSampleSize / 8, ty, ty + legendSampleSize / 8};
              // antialiasing needs to be on for vector, but already on for SgtUtil.drawHtmlText
              g2.fillPolygon(xPoints, yPoints, 3);
              g2.drawPolygon(xPoints, yPoints, 3);
            } else {
              Test.error(
                  String2.ERROR
                      + " in SgtMap.makeMap: Unsupported GraphDataLayer.draw value: "
                      + gdl.draw
                      + "\nspl="
                      + gdl.toString());
            }

            // point legend text
            legendTextY =
                SgtUtil.belowLegendText(
                    g2,
                    legendTextX,
                    legendTextY,
                    fontFamily,
                    labelHeightPixels,
                    gdl.boldTitle == null
                        ? null
                        : SgtUtil.makeShortLines(maxBoldCharsPerLine, gdl.boldTitle, null, null),
                    SgtUtil.makeShortLines(maxCharsPerLine, gdl.title2, gdl.title3, gdl.title4));
          } else {
            // draw LEGEND_RIGHT
            if (gdl.draw == GraphDataLayer.DRAW_MARKERS
                || gdl.draw == GraphDataLayer.DRAW_MARKERS_AND_LINES) {
              int tSize =
                  labelHeightPixels
                      * 3
                      / 5; // nice size; not meant to match GraphDataLayer.markerSize
              int tx = legendTextX - tSize / 2;
              int ty = legendTextY - labelHeightPixels;
              if (gdl.draw == GraphDataLayer.DRAW_MARKERS_AND_LINES) {
                g2.setColor(gdl.lineColor);
                g2.drawLine(
                    tx + tSize / 2 - legendSampleSize / 2,
                    ty + tSize / 2,
                    tx + tSize / 2 + legendSampleSize / 2,
                    ty + tSize / 2);
              }
              if (gdl.markerType == GraphDataLayer.MARKER_TYPE_SQUARE) {
                g2.setColor(Color.white);
                g2.fillRect(tx, ty, tSize, tSize);
                g2.setColor(gdl.lineColor);
                g2.drawRect(tx, ty, tSize, tSize);
              }
            } else if (gdl.draw == GraphDataLayer.DRAW_POINT_VECTORS
                || gdl.draw == GraphDataLayer.DRAW_GRID_VECTORS) {
              g2.setColor(legendVectorColor);
              legendTextY -= labelHeightPixels / 2; // to center of vector
              g2.drawLine(
                  legendTextX - legendSampleSize / 2,
                  legendTextY,
                  legendTextX + legendSampleSize / 2,
                  legendTextY);
              int xPoints[] =
                  new int[] {
                    legendTextX + legendSampleSize / 4, // 1/2 to right - 1/4 for head
                    legendTextX + legendSampleSize / 2,
                    legendTextX + legendSampleSize / 4
                  };
              int yPoints[] =
                  new int[] {
                    legendTextY - legendSampleSize / 8,
                    legendTextY,
                    legendTextY + legendSampleSize / 8
                  };
              // antialiasing needs to be on for vector, but already on for SgtUtil.drawHtmlText
              g2.fillPolygon(xPoints, yPoints, 3);
              g2.drawPolygon(xPoints, yPoints, 3);
              legendTextY += labelHeightPixels * 3 / 2; // from center of vector
            } else {
              Test.error(
                  String2.ERROR
                      + " in SgtMap.makeMap: Unsupported GraphDataLayer.draw value: "
                      + gdl.draw
                      + "\nspl="
                      + gdl.toString());
            }
            legendTextY += labelHeightPixels;

            // point legend text
            legendTextY =
                SgtUtil.drawHtmlText(
                    g2,
                    legendTextX,
                    legendTextY,
                    1,
                    fontFamily,
                    labelHeightPixels,
                    false,
                    "<strong>" + SgtUtil.encodeAsHtml(gdl.boldTitle) + "</strong>");
            legendTextY =
                SgtUtil.drawHtmlText(
                    g2,
                    legendTextX,
                    legendTextY,
                    1,
                    fontFamily,
                    labelHeightPixels,
                    false,
                    SgtUtil.encodeAsHtml(gdl.title2));
            legendTextY =
                SgtUtil.drawHtmlText(
                    g2,
                    legendTextX,
                    legendTextY,
                    1,
                    fontFamily,
                    labelHeightPixels,
                    false,
                    SgtUtil.encodeAsHtml(gdl.title3));
            legendTextY =
                SgtUtil.drawHtmlText(
                    g2,
                    legendTextX,
                    legendTextY,
                    1,
                    fontFamily,
                    labelHeightPixels,
                    false,
                    SgtUtil.encodeAsHtml(gdl.title4));
          }
          if (reallyVerbose)
            String2.log(
                "  graphDataLayer" + i + " time=" + (System.currentTimeMillis() - tTime) + "ms");
        }

        // *** draw a graph with the AXIS LINES and actually draw the background color
        // This avoids anti-aliasing problems when axis labels drawn 2+ times.
        // Draw this last, so axis lines drawn over data at the edges.
        int grx1 = 0, gry1 = 0, grWidth = 0, grHeight = 0;
        if (!transparent) {
          CartesianGraph graph = new CartesianGraph("", xt, yt);
          Layer layer = new Layer("axis", layerDimension2D);
          layerNames.add(layer.getId());
          jPane.add(layer); // calls layer.setPane(this);
          layer.setGraph(graph); // calls graph.setLayer(this);
          // no clipping needed
          DecimalDegreeFormatter ddf = new DecimalDegreeFormatter(); // was DegreeMinuteFormatter

          // create the x axes
          PlainAxis2 xAxis = new PlainAxis2(ddf);
          xAxis.setRangeU(xUserRange);
          xAxis.setLocationU(origin);
          int nSmallTics = Math2.roundToInt(majorIncrement / minorIncrement) - 1;
          xAxis.setNumberSmallTics(nSmallTics);
          xAxis.setLabelInterval(1);
          xAxis.setLabelFont(labelFont);
          xAxis.setLabelFormat("%g°");
          xAxis.setLabelHeightP(axisLabelHeight);
          xAxis.setSmallTicHeightP(fontScale * .02);
          xAxis.setLargeTicHeightP(fontScale * .05);
          // SGLabel title = new SGLabel("", "X Axis", new Point2D.Double(0, 0));
          // title.setAlign(SGLabel.TOP, 1);
          // title.setHeightP(0.15);
          // title.setFont(labelFont);
          // xAxis.setTitle(title);

          PlainAxis2 topXAxis = new PlainAxis2(ddf);
          topXAxis.setRangeU(xUserRange);
          topXAxis.setLocationU(new Point2D.Double(xUserRange.start, yUserRange.end));
          // topXAxis.setTicPosition(Axis.POSITIVE_SIDE); //doesn't work: Axis.NO_LABEL);
          topXAxis.setSmallTicHeightP(0);
          topXAxis.setLargeTicHeightP(0);
          topXAxis.setLabelPosition(Axis.NO_LABEL);

          // create the y axes
          PlainAxis2 yAxis = new PlainAxis2(ddf);
          yAxis.setRangeU(yUserRange);
          yAxis.setLocationU(origin);
          yAxis.setNumberSmallTics(nSmallTics);
          yAxis.setLabelInterval(1);
          yAxis.setLabelFont(labelFont);
          yAxis.setLabelFormat("%g°");
          yAxis.setLabelHeightP(axisLabelHeight);
          yAxis.setSmallTicHeightP(fontScale * .02);
          yAxis.setLargeTicHeightP(fontScale * .05);

          PlainAxis2 rightYAxis = new PlainAxis2(ddf);
          rightYAxis.setRangeU(yUserRange);
          rightYAxis.setLocationU(new Point2D.Double(xUserRange.end, yUserRange.start));
          rightYAxis.setNumberSmallTics(nSmallTics);
          // rightYAxis.setTicPosition(Axis.POSITIVE_SIDE); //doesn't work: Axis.NO_LABEL);
          rightYAxis.setSmallTicHeightP(0);
          rightYAxis.setLargeTicHeightP(0);
          rightYAxis.setLabelPosition(Axis.NO_LABEL);

          graph.addXAxis(xAxis);
          graph.addXAxis(topXAxis);
          graph.addYAxis(yAxis);
          graph.addYAxis(rightYAxis);

          // draw the graph background color right before drawing the graph
          grx1 = graph.getXUtoD(xUserRange.start);
          gry1 = graph.getYUtoD(yUserRange.end);
          grWidth = graph.getXUtoD(xUserRange.end) - grx1;
          grHeight = graph.getYUtoD(yUserRange.start) - gry1;
          g2.setColor(oceanColor);
          g2.fillRect(grx1, gry1, grWidth, grHeight);
        }

        // return antialiasing to original
        if (originalAntialiasing != null)
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialiasing);

        // actually draw everything
        if (colorMap != null) colorMap.resetStats();
        if (reallyVerbose)
          String2.log("  set up the graph time=" + (System.currentTimeMillis() - time) + "ms");
        // String2.log("  before jPane.draw: " + Math2.memoryString());
        time = System.currentTimeMillis();
        jPane.draw(g2); // comment out for memory leak tests

        // draw NoData?
        // String2.log("noData=" + noData);
        // if (noData) {
        //    g2.setColor(Color.black);
        //    g2.setFont(labelFont.deriveFont(labelFont.getSize2D() * 1.5f * (float)fontScale));
        //    //String2.log("NO DATA: drawing at " + (grx1 + grWidth / 2) + ", " + (gry1 + grHeight
        // / 2));
        //    g2.drawString("No Data",
        //        grx1 + grWidth / 2 - Math2.roundToInt(17 * 1.5f * fontScale),
        //        gry1 + grHeight / 2);
        // }

        // gather up all of the data for the user map
        for (VectorPointsRenderer vectorPointsRenderer : vectorPointsRenderers) {
          int tn = vectorPointsRenderer.resultBaseX.size();
          int halfBox = 4; // half of box size, in pixels
          for (int ti = 0; ti < tn; ti++) {
            resultMinX.add(vectorPointsRenderer.resultBaseX.get(ti) - halfBox);
            resultMaxX.add(vectorPointsRenderer.resultBaseX.get(ti) + halfBox);
            resultMinY.add(vectorPointsRenderer.resultBaseY.get(ti) - halfBox);
            resultMaxY.add(vectorPointsRenderer.resultBaseY.get(ti) + halfBox);
            resultRowNumber.add(vectorPointsRenderer.resultRowNumber.get(ti));
            resultPointScreen.add(
                ((VectorAttribute2) vectorPointsRenderer.getAttribute()).sourceID);
          }
        }
        vectorPointsRenderers = null; // so garbage-collectable  (see next section)
        for (int i = 0; i < filledMarkerRenderers.length; i++) {
          if (filledMarkerRenderers[i] != null) {
            int oldN = resultMinX.size();
            resultMinX.append(filledMarkerRenderers[i].resultMinX);
            resultMaxX.append(filledMarkerRenderers[i].resultMaxX);
            resultMinY.append(filledMarkerRenderers[i].resultMinY);
            resultMaxY.append(filledMarkerRenderers[i].resultMaxY);
            resultRowNumber.append(filledMarkerRenderers[i].resultRowNumber);
            int newN = resultMinX.size();
            int tWhichPointScreen = filledMarkerRenderers[i].sourceID;
            // String2.log("  tWhichPointScreen = " + tWhichPointScreen);
            for (int j = oldN; j < newN; j++) resultPointScreen.add(tWhichPointScreen);
            filledMarkerRenderers[i] = null; // so garbage-collectable  (see next section)
          }
        }
      } catch (Exception e) {
        // String2.log(String2.ERROR + " in SgtMap.makeMap: " + MustBe.throwableToString(e));
        thrownException = e;
      }

      // deconstruct jPane
      deconstructJPane("SgtMap.makeMap", jPane, layerNames);

      // ok, all cleaned up
      if (thrownException != null) throw thrownException;

      if (reallyVerbose) {
        if (colorMap != null) String2.log(colorMap.getStats());
        String2.log(
            "  SgtMap.makeMap draw the graph time=" + (System.currentTimeMillis() - time) + "ms");
        // Math2.gcAndWait("SgtGraph (debugMode)"); //Part of debug.  Before getMemoryString().
        // Outside of timing system.
        // String2.log("  SgtMap.makeMap after jPane.draw: " + Math2.memoryString());
        // String2.log("  SgtMap.makeMap after gc: " + Math2.memoryString());
      }

      // test
      // String2.log("ImageIO Readers: " + String2.toCSSVString(ImageIO.getReaderFormatNames()) +
      //          "\nImageIO Writers: " + String2.toCSSVString(ImageIO.getWriterFormatNames()));

      // display time to makeMap
      if (verbose)
        String2.log(
            "}} SgtMap.makeMap done. TOTAL TIME="
                + (System.currentTimeMillis() - startTime)
                + "ms\n");
      g2.setClip(null); // clear the clip region

      // return the results
      return results;
    }
  }

  /**
   * Deconstruct JPane to avoid memory leak in sgt.
   *
   * <p>Problem: JPane and all subcomponents don't seem to be garbage-collected as one would expect
   * (since they are created and used only in this method).
   *
   * <p>Note that the parts all have references to each other (e.g., JPane keeps track of Layers and
   * Layers know their JPane, and similarly Layers have links to/from Graphs and Graphs have links
   * to/from Renderers(SimpleGrid + Attribute). It is all one big, bidirectionally-linked blob.
   *
   * <p>Possible Cause 1: There could be so many links (and cross-links?) than the gc can't release
   * any of it.
   *
   * <p>Possible Cause 2: There could be a link to the JPane (or some other part of the graph) held
   * externally (like, but not, the mouse event listeners in JPane and Pane, or
   * jPane.removeNotify(), or Swing?) which is preventing the blob from being gc'd. I looked for,
   * but never found, any actual cause like this.
   *
   * <p>Solution: deal with possible cause 1: I manually removed links between parts of the JPane,
   * Layers, Graphs, and parts of the graph. I really thought culprit was cause 2, but the success
   * of this solution is evidence that cause 1 is the culprit. But bathymetry and boundary
   * CartesianGraphs still not being gc'd until program shuts down, although their components are.
   * They are 56 bytes each, to it will take 1,000,000 before trouble. Are links to these the source
   * of Cause 2?
   *
   * @param methodName for diagnostic messages
   * @param jPane
   * @param layerNames
   */
  public static void deconstructJPane(String methodName, JPane jPane, StringArray layerNames) {
    // 2011-01-11 This problem seems to be fixed, so I am commenting out this code.
    // Original problem was with Java 1.4, so maybe Java 1.6 fixes it.
    // Future improvement if needed: make layerList, so layers available,
    // so no need to call jPane.getLayer, which often/usually fails (why?!),
    // and generates lots of exceptions which are a waste of time.

    // 2011-05-20 Java 64 bit has memory problem. I can't test on my desktop PC.
    // So for safety, I re-enabled this code.
    // 2011-06-14 old code was insufficent. I added extensive releaseResources() system.

    if (reallyVerbose) String2.log(methodName + " deconstructJPane");

    // *** set JPane.debug = true ONLY for local debugging.
    // Methods wait for input from me if trouble!
    JPane.debug = false;

    // break links to/from jPane,components(which are layers),graphs,partsOfGraphs,
    //   layerChildren
    // Be careful when writing releaseResources() methods -- danger of infinite loop
    try {
      jPane.releaseResources();
    } catch (Throwable t) {
      String2.log(MustBe.throwableToString(t));
      if (JPane.debug) {
        try {
          String2.pressEnterToContinue();
        } catch (Throwable t2) {
        }
      }
    }
  }

  /**
   * Given that the map will be isotropic, reduce areaWidth areaHeight to estimate the approximate
   * graphWidth and height. This is important for opendap datasets: reduce amount of data needed to
   * download. reserve at least 40 x pixels for labels, y axis labels, borders (this is
   * conservative).
   *
   * @param fontScale usually 1
   * @param areaWidth the area allotted to the graph and legend on the image
   * @param areaHeight the area allotted to the graph and legend on the image
   * @param minX the min lon of the graph
   * @param maxX the max lon of the graph
   * @param minY the min lat of the graph
   * @param maxY the max lat of the graph
   * @return int[]{graphWidth, graphHeight}
   */
  public static int[] predictGraphSize(
      double fontScale,
      int areaWidth,
      int areaHeight,
      double minX,
      double maxX,
      double minY,
      double maxY) {

    if (reallyVerbose)
      String2.log(
          "SgtMap.predictGraphSize  areaWidth="
              + areaWidth
              + " areaHeight="
              + areaHeight
              + " minX="
              + minX
              + " maxX="
              + maxX
              + " minY="
              + minY
              + " maxY="
              + maxY);
    int graphWidth = areaWidth - Math2.roundToInt(fontScale * 40);
    // reserve at least 80 y pixels for legend, x axis labels, borders (this is conservative)
    int graphHeight = areaHeight - Math2.roundToInt(fontScale * 80);
    // which is limiting direction?
    double xRange = maxX - minX;
    double yRange = maxY - minY;
    // if not isotropic and graph expanded to fill available area,
    // how many degrees packed into how many pixels?
    double xPack = xRange / graphWidth;
    double yPack = yRange / graphHeight;
    // is x or y a tighter fit (limiting) (more degrees per pixel)?
    // adjust the other direction to be as tightly packed
    if (xPack > yPack) graphHeight = Math2.roundToInt(yRange / xPack);
    else graphWidth = Math2.roundToInt(xRange / yPack);
    if (reallyVerbose)
      String2.log(
          "  SgtMap.predictGraphSize done. graphWidth="
              + graphWidth
              + " graphHeight="
              + graphHeight);
    return new int[] {graphWidth, graphHeight};
  }

  /**
   * This uses SgtMap to plot grid data (and nothing else) on a map that is a specific size. Strings
   * should be "" if not needed. This is not static because it uses boundary. This does not draw a
   * background color for the image.
   *
   * @param minX the min lon value on the map and appropriate for the data
   * @param maxX the max lon value on the map and appropriate for the data
   * @param minY the min lat value on the map
   * @param maxY the max lat value on the map
   * @param grid the grid to be plotted (or null if none). It may span a larger area than the
   *     desired map.
   * @param gridScaleFactor is a scale factor to be applied to the data (use "1" if none)
   * @param gridAltScaleFactor is a scale factor to be applied to the data (use "1" if none)
   * @param gridAltOffset is a scale factor to be added to the data (use "0" if none)
   * @param gridPaletteFileName is the complete name of the palette file to be used
   * @param drawLakesAndRivers one of the LAKES_AND_RIVERS constants from above. But even if true,
   *     they are never drawn if resolution = 'c'
   * @param g2 the graphics2D object to be used (the image background color should already have been
   *     drawn)
   * @param baseULXPixel defines area to be used, in pixels
   * @param baseULYPixel defines area to be used, in pixels
   * @param graphWidthPixels defines area to be used, in pixels
   * @param graphHeightPixels defines area to be used, in pixels
   * @throws Exception
   */
  public static void makeCleanMap(
      double minX,
      double maxX,
      double minY,
      double maxY,
      boolean drawLandUnder,
      Grid grid,
      double gridScaleFactor,
      double gridAltScaleFactor,
      double gridAltOffset,
      String gridPaletteFileName,
      boolean drawLandOver,
      boolean drawCoastline,
      int drawLakesAndRivers,
      boolean drawNationalBoundaries,
      boolean drawStateBoundaries,
      Graphics2D g2,
      int imageWidth,
      int imageHeight,
      int baseULXPixel,
      int baseULYPixel,
      int graphWidthPixels,
      int graphHeightPixels)
      throws Exception {

    // Coordinates in SGT:
    //   Graph - 'U'ser coordinates      (graph's axes' coordinates)
    //   Layer - 'P'hysical coordinates  (e.g., pseudo-inches, 0,0 is lower left)
    //   JPane - 'D'evice coordinates    (pixels, 0,0 is upper left)

    if (!drawPoliticalBoundaries) {
      drawNationalBoundaries = false;
      drawStateBoundaries = false;
    }

    // set the clip region
    g2.setClip(null); // clear any previous clip region  //this is necessary!
    g2.clipRect(baseULXPixel, baseULYPixel, graphWidthPixels, graphHeightPixels);
    {
      if (reallyVerbose)
        String2.log(
            "\nSgtMap.makeCleanMap "
                + Math2.memoryString()
                + "\n baseULXPixel="
                + baseULXPixel
                + " graphWidth="
                + graphWidthPixels
                + "\n baseULYPixel="
                + baseULYPixel
                + " graphHeight="
                + graphHeightPixels);
      long startTime = System.currentTimeMillis();
      long time = System.currentTimeMillis();

      // figure out the params needed to make the map
      double xRange = maxX - minX;
      double yRange = maxY - minY;
      double maxRange = Math.max(xRange, yRange);
      int boundaryResolution =
          suggestBoundaryResolution(maxRange, Math.max(graphWidthPixels, graphHeightPixels), 0);

      // create the pane
      JPane jPane = new JPane("", new java.awt.Dimension(imageWidth, imageHeight));
      jPane.setLayout(new StackedLayout());

      // create the common graph parts
      // graph's physical location (in image pixels) (start, end, delta); delta is ignored
      Range2D xPhysRange = new Range2D(baseULXPixel, baseULXPixel + graphWidthPixels, 1);
      Range2D yPhysRange =
          new Range2D(
              imageHeight - (baseULYPixel + graphHeightPixels), imageHeight - baseULYPixel, 1);
      Range2D xUserRange = new Range2D(minX, maxX, 1);
      Range2D yUserRange = new Range2D(minY, maxY, 1);
      gov.noaa.pmel.sgt.LinearTransform xt =
          new gov.noaa.pmel.sgt.LinearTransform(xPhysRange, xUserRange);
      gov.noaa.pmel.sgt.LinearTransform yt =
          new gov.noaa.pmel.sgt.LinearTransform(yPhysRange, yUserRange);
      Dimension2D layerDimension2D = new Dimension2D(imageWidth, imageHeight);
      StringArray layerNames = new StringArray();
      if (drawLakesAndRivers < NO_LAKES_AND_RIVERS) drawLakesAndRivers = SgtMap.NO_LAKES_AND_RIVERS;
      if (drawLakesAndRivers > FILL_LAKES_AND_RIVERS)
        drawLakesAndRivers = SgtMap.FILL_LAKES_AND_RIVERS;

      // *** draw land under
      if (drawLandUnder) {
        CartesianGraph graph = new CartesianGraph("", xt, yt);
        Layer layer = new Layer("landunder", layerDimension2D);
        layerNames.add(layer.getId());
        jPane.add(layer); // calls layer.setPane(this);
        layer.setGraph(graph); // calls graph.setLayer(this);

        // assign the data   (PathCartesionRenderer always clips by itself)
        graph.setRenderer(
            new PathCartesianRenderer(
                graph,
                GSHHS.getGeneralPath(
                    GSHHS.RESOLUTIONS.charAt(boundaryResolution),
                    1, // just get land info
                    minX,
                    maxX,
                    minY,
                    maxY,
                    true),
                1e-6,
                landColor, // fillColor
                landColor)); // strokeColor
      }

      // *** create a layer with the GRID DATA graph
      // colorMap outside loop since timing info is gathered below
      CompoundColorMap colorMap = null;
      if (grid != null) {
        colorMap = new CompoundColorMap(gridPaletteFileName);
        CartesianGraph graph = new CartesianGraph("", xt, yt);
        Layer layer = new Layer("grid", layerDimension2D);
        layerNames.add(layer.getId());
        jPane.add(layer); // calls layer.setPane(this);
        layer.setGraph(graph); // calls graph.setLayer(this);
        graph.setClip(
            xUserRange.start, xUserRange.end,
            yUserRange.start, yUserRange.end);
        graph.setClipping(true);

        // get the Grid
        long readTime = System.currentTimeMillis();
        // this doesn't change the +/-180 status (just checks for odd situations).
        // this does subset the data if more dense or over a bigger range then nec.
        // 2017-10-23 This isn't needed (at least for ERDDAP, not so sure about CWBrowsers)
        //  and has a serious bug (see erdVH3chla8day in leaflet)
        //  that sometimes erroneously tries to change the pm180 status by making a huge array,
        //  that leads to out-of-memory.
        //                grid.makeLonPM180AndSubset(minX, maxX, minY, maxY,
        //                    graphWidthPixels, graphHeightPixels);
        if (reallyVerbose)
          String2.log(
              "  SgtMap.makeCleanMap grid readGrd time="
                  + (System.currentTimeMillis() - readTime)
                  + "ms");
        DataHelper.scale(grid.data, gridScaleFactor * gridAltScaleFactor, gridAltOffset);
        SimpleGrid simpleGrid = new SimpleGrid(grid.data, grid.lon, grid.lat);

        // assign the data
        graph.setData(simpleGrid, new GridAttribute(GridAttribute.RASTER, colorMap));
      }

      // *** draw land over
      if (drawLandOver || drawCoastline) {
        // draw land
        CartesianGraph graph = new CartesianGraph("", xt, yt);
        Layer layer = new Layer("landover", layerDimension2D);
        layerNames.add(layer.getId());
        jPane.add(layer); // calls layer.setPane(this);
        layer.setGraph(graph); // calls graph.setLayer(this);

        // assign the data   (PathCartesionRenderer always clips by itself)
        graph.setRenderer(
            new PathCartesianRenderer(
                graph,
                GSHHS.getGeneralPath(
                    GSHHS.RESOLUTIONS.charAt(boundaryResolution),
                    1, // just get land info
                    minX,
                    maxX,
                    minY,
                    maxY,
                    true),
                1e-6,
                drawLandOver ? landColor : null, // fillColor
                drawCoastline ? landMaskStrokeColor : landColor)); // strokeColor
      }

      if (drawLakesAndRivers
          != NO_LAKES_AND_RIVERS) { // && boundaryResolution != CRUDE_RESOLUTION) {
        // String2.log("SgtMap.makeCleanMap drew lakesAndRivers");
        {
          // draw Lakes
          CartesianGraph graph = new CartesianGraph("", xt, yt);
          Layer layer = new Layer("lakesover", layerDimension2D);
          layerNames.add(layer.getId());
          jPane.add(layer); // calls layer.setPane(this);
          layer.setGraph(graph); // calls graph.setLayer(this);

          // assign the data   (PathCartesionRenderer always clips by itself)
          graph.setRenderer(
              new PathCartesianRenderer(
                  graph,
                  GSHHS.getGeneralPath(
                      GSHHS.RESOLUTIONS.charAt(boundaryResolution),
                      2, // just get lake info
                      minX,
                      maxX,
                      minY,
                      maxY,
                      true),
                  1e-6,
                  drawLakesAndRivers == FILL_LAKES_AND_RIVERS ? lakesColor : null, // fillColor
                  lakesColor)); // strokeColor
        }

        {
          // draw rivers
          CartesianGraph graph = new CartesianGraph("", xt, yt);
          Layer layer = new Layer("riversover", layerDimension2D);
          layerNames.add(layer.getId());
          jPane.add(layer); // calls layer.setPane(this);
          layer.setGraph(graph); // calls graph.setLayer(this);
          graph.setClip(
              xUserRange.start, xUserRange.end,
              yUserRange.start, yUserRange.end);
          graph.setClipping(true);

          LineAttribute lineAttribute = new LineAttribute();
          lineAttribute.setColor(riversColor);
          graph.setData(
              rivers.getSgtLine(boundaryResolution, minX, maxX, minY, maxY), lineAttribute);
        }
      }

      // *** draw the StateBOUNDARY
      if (drawStateBoundaries) {
        CartesianGraph graph = new CartesianGraph("", xt, yt);
        Layer layer = new Layer("stateBoundary", layerDimension2D);
        layerNames.add(layer.getId());
        jPane.add(layer); // calls layer.setPane(this);
        layer.setGraph(graph); // calls graph.setLayer(this);
        graph.setClip(
            xUserRange.start, xUserRange.end,
            yUserRange.start, yUserRange.end);
        graph.setClipping(true);

        LineAttribute lineAttribute = new LineAttribute();
        lineAttribute.setColor(statesColor);
        graph.setData(
            stateBoundaries.getSgtLine(boundaryResolution, minX, maxX, minY, maxY), lineAttribute);
      }

      // *** draw the NationalBOUNDARY
      if (drawNationalBoundaries) {
        CartesianGraph graph = new CartesianGraph("", xt, yt);
        Layer layer = new Layer("nationalBoundary", layerDimension2D);
        layerNames.add(layer.getId());
        jPane.add(layer); // calls layer.setPane(this);
        layer.setGraph(graph); // calls graph.setLayer(this);
        graph.setClip(
            xUserRange.start, xUserRange.end,
            yUserRange.start, yUserRange.end);
        graph.setClipping(true);

        LineAttribute lineAttribute = new LineAttribute();
        lineAttribute.setColor(nationsColor);
        graph.setData(
            nationalBoundaries.getSgtLine(boundaryResolution, minX, maxX, minY, maxY),
            lineAttribute);
      }

      // actually draw everything
      // String2.log("  SgtMap.makeCleanMap before jPane.draw: " + Math2.memoryString());
      jPane.draw(g2); // comment out for memory leak tests

      // deconstruct jPane
      deconstructJPane("SgtMap.makeCleanMap", jPane, layerNames);

      if (reallyVerbose) {
        if (colorMap != null) String2.log(colorMap.getStats());
        String2.log(
            "  SgtMap.makeCleanMap draw graph time=" + (System.currentTimeMillis() - time) + "ms");
        // Math2.gcAndWait("SgtGraph (debugMode)"); //Part of debug.  Before getMemoryString().
        // Outside of timing system.
        // String2.log("  SgtMap.makeCleanMap after jPane.draw: " + Math2.memoryString());
        // String2.log("  SgtMap.makeCleanMap after gc: " + Math2.memoryString());
      }

      // display time to makeCleanMap
      if (verbose)
        String2.log(
            "  SgtMap.makeCleanMap done. res="
                + boundaryResolution
                + " Total TIME="
                + (System.currentTimeMillis() - startTime)
                + "ms");
      g2.setClip(null); // clear the clip region
    }
  }

  /**
   * This returns a grid with bathymetry data from etopo1g for the specified region and graph
   * dimensions. The grid will have the correct stats for this subsample of the data. The data is
   * "grid centered", so the data associated with a given lon,lat represents the data from a cell
   * centered on that lon,lat. Data source: https://www.ngdc.noaa.gov/mgg/global/global.html
   *
   * <p>Currently, the desired grid is created and then each point in the grid is populated by
   * finding the single nearest point in etopo1g and using that value. Currently, the resulting grid
   * will have exactly graphWidth/HeightPixels (although less, if the file's doesn't have that many
   * points).
   *
   * @param fullPrivateDirectory the directory where cache files are to be stored (or null if you
   *     don't want to deal with caching).
   * @param minX the min longitude
   * @param maxX the max longitude
   * @param minY the min latitude
   * @param maxY the max latitude
   * @param graphWidthPixels use Integer.MAX_VALUE to get maximum resolution
   * @param graphHeightPixels use Integer.MAX_VALUE to get maximum resolution
   * @return the full name of the grd file (which will have been touched, so it won't be deleted
   *     soon).
   * @throws Exception if trouble
   */
  public static Grid createTopographyGrid(
      String fullPrivateDirectory,
      double minX,
      double maxX,
      double minY,
      double maxY,
      int graphWidthPixels,
      int graphHeightPixels)
      throws Exception {
    String topoFileName =
        String2.canonical(
            "Topography"
                + FileNameUtility.makeWESNString(minX, maxX, minY, maxY)
                + FileNameUtility.makeNLonNLatString(graphWidthPixels, graphHeightPixels));
    String fullTopoFileName = fullPrivateDirectory + topoFileName + ".grd";

    // synchronize on canonical topoFileName, so >1 simultaneous request won't be duplicated
    ReentrantLock lock = String2.canonicalLock(topoFileName);
    if (!lock.tryLock(String2.longTimeoutSeconds, TimeUnit.SECONDS))
      throw new TimeoutException("Timeout waiting for lock on topoFileName in SgtMap.");
    try {

      // these get reused a lot, so cache them
      // does the file already exist?
      if (fullPrivateDirectory != null) {
        if (File2.touch(fullTopoFileName)) {
          try {
            EDStatic.metrics.sgtMapTopoRequest.labelValues(Metrics.Cache.cached.name()).inc();
            Grid grid = new Grid();
            grid.readGrd(
                fullTopoFileName, minX, maxX, minY, maxY, graphWidthPixels, graphHeightPixels);
            // 2014-01-08 new is above. Old is below. But old is silly and sometimes fails.
            // The file has what we want. Just read it.
            // grid.readGrd(fullPrivateDirectory + topoFileName + ".grd",
            //    DataHelper.lonNeedsToBePM180(minX, maxX));
            if (reallyVerbose)
              String2.log(
                  "  createTopographyGrid "
                      + topoFileName
                      + " nFromCache="
                      + topoCache()
                      + "* nNotFromCache="
                      + topoNotCache());
            return grid;
          } catch (Throwable t) {
            String2.log(
                MustBe.throwableToString(t)
                    + "Caught that ERROR while reading cached topo file\n  "
                    + fullTopoFileName
                    + "\n  So deleting and recreating the grid and the file.");
            File2.delete(fullTopoFileName);
          }
        }
      }

      // create the grid;  readBinary calculates stats
      // On 2011-03-14 I switched to etopo1_ice_g_i2.bin
      //  grid referenced, 16bit ints, 10801 rows by 21601 columns
      // On 2007-03-29 I switched from ETOPO2 (version 1) to ETOPO2v2g_MSB.raw (version 2).
      //  ETOPO2v2g_MSB.raw (grid centered, MSB 16 bit signed integers)
      //  5401 rows by 10801 columns.
      // Data is stored row by row, starting at 90, going down to -90,
      // with lon -180 to 180 on each row (the first and last points on each row are duplicates).
      // The data is grid centered, so a given lon,lat is the center of a cell.
      // I verified this interpretation with Lynn.
      Grid grid = new Grid();
      grid.readBinary(
          fullEtopoFileName,
          -180,
          180, // these settings are specific for the ETOPO1g file
          -90,
          90,
          21601,
          10801,
          minX,
          maxX,
          minY,
          maxY,
          graphWidthPixels,
          graphHeightPixels);

      // cache it  (if not huge)
      if (fullPrivateDirectory != null && grid.lon.length < 600 && grid.lat.length < 600) {
        grid.saveAsGrd(fullPrivateDirectory, topoFileName); // this calls calculateStats
      }
      EDStatic.metrics.sgtMapTopoRequest.labelValues(Metrics.Cache.not_cached.name()).inc();
      if (reallyVerbose)
        String2.log(
            "  createTopographyGrid "
                + topoFileName
                + " nFromCache="
                + topoCache()
                + " nNotFromCache="
                + topoNotCache()
                + "*");

      return grid;
    } finally {
      lock.unlock();
    }
  }

  private static long topoCache() {
    return EDStatic.metrics
        .sgtMapTopoRequest
        .labelValues(Metrics.Cache.cached.name())
        .getLongValue();
  }

  private static long topoNotCache() {
    return EDStatic.metrics
        .sgtMapTopoRequest
        .labelValues(Metrics.Cache.not_cached.name())
        .getLongValue();
  }

  /** Returns the topography stats string. */
  public static String topographyStats() {
    return "SgtMap topography nFromCache=" + topoCache() + " nNotFromCache=" + topoNotCache();
  }

  // *** Junk Yard *******
  // create the colorbar for the legend
  /*ColorKey colorKey = new ColorKey(new Point2D.Double(4.5, 3), //location
      new Dimension2D(0.25, 2.5), //size
      ColorKey.TOP, ColorKey.LEFT); //valign, halign
  colorKey.setOrientation(ColorKey.VERTICAL);
  colorKey.setBorderStyle(ColorKey.NO_BORDER);
  colorKey.setColorMap(colorMap);
  Ruler ruler = colorKey.getRuler();
  ruler.setLabelFont(labelFont);
  ruler.setLabelHeightP(0.15);
  ruler.setLabelInterval(2); //temp
  ruler.setLargeTicHeightP(0.04);
  ruler.setRangeU(colorMap.getRange());
  String2.log("colorMap start=" + colorMap.getRange().start + " end=" +
      colorMap.getRange().end + " delta=" + colorMap.getRange().delta);
  layer.addChild(colorKey);
  */
}
