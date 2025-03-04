/*
 * $Id: TimeAxis.java,v 1.15 2003/08/22 23:02:32 dwd Exp $
 *
 * This software is provided by NOAA for full, free and open release.  It is
 * understood by the recipient/user that NOAA assumes no liability for any
 * errors contained in the code.  Although this software is released without
 * conditions or restrictions in its use, it is expected that appropriate
 * credit be given to its author and to the National Oceanic and Atmospheric
 * Administration should the software be included by the recipient as an
 * element in other product development.
 */

package gov.noaa.pmel.sgt;

import gov.noaa.pmel.util.Debug;
import gov.noaa.pmel.util.GeoDate;
import gov.noaa.pmel.util.Point2D;
import gov.noaa.pmel.util.SoTPoint;
import gov.noaa.pmel.util.SoTRange;
import gov.noaa.pmel.util.SoTValue;
import gov.noaa.pmel.util.TimePoint;
import gov.noaa.pmel.util.TimeRange;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.ArrayList;

// jdk1.2
// import java.awt.geom.Point2D;

/**
 * Base class for time axes. A time axis is an axis whose user units are GeoDate objects.
 *
 * @author Donald Denbo
 * @version $Revision: 1.15 $, $Date: 2003/08/22 23:02:32 $
 * @since 1.0
 * @see Axis
 */
public class TimeAxis extends Axis implements Cloneable {
  //
  protected TimeRange tRange_;
  protected TimePoint tLocation_;
  //
  protected String minorLabelFormat_;
  protected int minorLabelInterval_;
  protected String majorLabelFormat_;
  protected int majorLabelInterval_;
  //
  protected double yminor_;
  //
  protected double ymajor_;
  protected double xminor_;
  protected double xmajor_;
  protected int vertalign_;

  //
  /**
   * @shapeType AggregationLink
   * @associates <strong>TimeAxisStyle</strong>
   * @supplierCardinality 1
   * @byValue
   */
  public TimeAxisStyle txt_; // bob changed; was protected

  protected int axisStyle_;
  static final double TIC_RATIO__ = 1.3;
  static final double TIC_GAP__ = 0.05;
  static final double LABEL_RATIO__ = 1.0;
  static final double MAJOR_LABEL_RATIO__ = 1.25;

  static final double defaultLargeTicHeight__ = 0.1;
  static final double defaultSmallTicHeight__ = 0.05;
  static final int defaultTicPosition__ = Axis.NEGATIVE_SIDE;
  static final int defaultLabelPosition__ = Axis.NEGATIVE_SIDE;
  static final double defaultLabelHeight__ = 0.15;

  /** Automatically select the time axis style */
  public static final int AUTO = 0;

  /**
   * Use the YearDecadeAxis style.
   *
   * <pre>
   *   |..........|..........|..........|..........|
   *        84         85         86         87
   *                       1980
   * </pre>
   */
  public static final int YEAR_DECADE = 1;

  /**
   * Use the MonthYearAxis style.
   *
   * <pre>
   *   |..........|..........|..........|..........|
   *        Mar        Apr        May       Jun
   *                       1980
   * </pre>
   */
  public static final int MONTH_YEAR = 2;

  /**
   * Use the DayMonthAxis style.
   *
   * <pre>
   *   |..........|..........|..........|..........|
   *        3          4          5           6
   *                        1993-04
   * </pre>
   */
  public static final int DAY_MONTH = 3;

  /**
   * Use the HourDayAxis style.
   *
   * <pre>
   *   |..........|..........|..........|..........|
   *   03         04         05         06         07
   *                     1987-06-07
   * </pre>
   */
  public static final int HOUR_DAY = 4;

  /**
   * Use the MinuteHourAxis style.
   *
   * <pre>
   *   |..........|..........|..........|..........|
   *   15         30         45         00         15
   *                   1987-06-07T13
   * </pre>
   */
  public static final int MINUTE_HOUR = 5;

  private void setAuto() {
    TimeAxisStyle newStyle = null;

    GeoDate delta = tRange_.end.subtract(tRange_.start);
    double days = ((double) Math.abs(delta.getTime())) / ((double) GeoDate.MSECS_IN_DAY);
    if (Debug.TAXIS) {
      System.out.println("setAuto: days = " + days);
    }
    if (days > 260000.0) { // ~800 years
      // System.out.println("CenturyAxis");
      if (!(txt_ instanceof CenturyAxis)) {
        newStyle = new CenturyAxis();
      }
    } else if (days > 26000.0) { // ~80 years
      // System.out.println("DecadeAxis");
      if (!(txt_ instanceof DecadeAxis)) {
        newStyle = new DecadeAxis();
      }
    } else if (days > 1000.0) { // ~3 years
      // System.out.println("YearDecadeAxis");
      if (!(txt_ instanceof YearDecadeAxis)) {
        newStyle = new YearDecadeAxis();
      }
    } else if (days > 91.0) {
      if (!(txt_ instanceof MonthYearAxis)) {
        newStyle = new MonthYearAxis();
      }
    } else if (days > 3.0) {
      if (!(txt_ instanceof DayMonthAxis)) {
        newStyle = new DayMonthAxis();
      }
    } else if (days > 0.1666666) { // 4 hours     (5 has cramped major labels)
      if (!(txt_ instanceof HourDayAxis)) {
        newStyle = new HourDayAxis();
      }
    } else if (days > 2.777777e-3) { // 4 minutes   (5 has cramped major labels)
      if (!(txt_ instanceof MinuteHourAxis)) {
        newStyle = new MinuteHourAxis();
      }
    } else if (days > 4.62961e-5) { // 4 seconds   (5 has cramped major labels)
      if (!(txt_ instanceof SecondMinuteAxis)) {
        newStyle = new SecondMinuteAxis();
      }
    } else {
      if (!(txt_ instanceof MilliSecondAxis)) {
        newStyle = new MilliSecondAxis();
      }
    }
    if (newStyle != null) {
      txt_ = newStyle;
      //    } else {
      //      return;
    }
    txt_.computeDefaults(delta);

    minorLabelFormat_ = txt_.getDefaultMinorLabelFormat();
    majorLabelFormat_ = txt_.getDefaultMajorLabelFormat();
    minorLabelInterval_ = txt_.getDefaultMinorLabelInterval();
    majorLabelInterval_ = txt_.getDefaultMajorLabelInterval();
    numSmallTics_ = txt_.getDefaultNumSmallTics();
    //    largeTicHeight_ = txt_.getDefaultLargeTicHeight();
    //    smallTicHeight_ = txt_.getDefaultSmallTicHeight();
    //    ticPosition_ = txt_.getDefaultTicPosition();
    //    labelPosition_ = txt_.getDefaultLabelPosition();
    //    labelHeight_ = txt_.getDefaultLabelHeight();

    if (Debug.TAXIS) {
      System.out.println(
          "    style, ticPosition, labelPostiion = "
              + txt_.toString()
              + ", "
              + ticPosition_
              + ", "
              + labelPosition_);
      System.out.println(
          "    minorFormat, majorFormat, minorInterval, majorInterval = "
              + minorLabelFormat_
              + ", "
              + majorLabelFormat_
              + ", "
              + minorLabelInterval_
              + ", "
              + majorLabelInterval_);
      System.out.println(
          "    smallTics, largeHgt, smallHgt, labelHgt = "
              + numSmallTics_
              + ", "
              + largeTicHeight_
              + ", "
              + smallTicHeight_
              + ", "
              + labelHeight_);
    }
  }

  @Override
  protected void updateRegisteredTransforms() {
    if (!registeredTransforms_.isEmpty()) {
      AxisTransform trns;
      for (Transform item : registeredTransforms_) {
        trns = (AxisTransform) item;
        trns.setRangeP(pRange_);
        trns.setRangeU(tRange_);
      }
    }
  }

  //
  @Override
  protected void updateRegisteredAxes() {
    if (!registeredAxes_.isEmpty()) {
      TimeAxis ax;
      for (Axis axis : registeredAxes_) {
        ax = (TimeAxis) axis;
        ax.setRangeU(tRange_);
        ax.setRangeP(pRange_);
      }
    }
  }

  protected void setupDraw(double val) {
    if (orientation_ == Axis.HORIZONTAL) {
      if (labelPosition_ == POSITIVE_SIDE) {
        vertalign_ = SGLabel.BOTTOM;
        if (minorLabelInterval_ == 0) {
          yminor_ = val;
        } else if (ticPosition_ == BOTH_SIDES || ticPosition_ == POSITIVE_SIDE) {
          yminor_ = val + TIC_RATIO__ * largeTicHeight_;
        } else {
          yminor_ = val + TIC_GAP__;
        }
        ymajor_ = yminor_ + LABEL_RATIO__ * labelHeight_;
      } else {
        vertalign_ = SGLabel.TOP;
        if (minorLabelInterval_ == 0) {
          yminor_ = val;
        } else if (ticPosition_ == BOTH_SIDES || ticPosition_ == NEGATIVE_SIDE) {
          yminor_ = val - TIC_RATIO__ * largeTicHeight_;
        } else {
          yminor_ = val - TIC_GAP__;
        }
        ymajor_ = yminor_ - LABEL_RATIO__ * labelHeight_;
      }
    } else {
      if (labelPosition_ == NEGATIVE_SIDE) {
        vertalign_ = SGLabel.BOTTOM;
        if (minorLabelInterval_ == 0) {
          xminor_ = val;
        } else if (ticPosition_ == BOTH_SIDES || ticPosition_ == NEGATIVE_SIDE) {
          xminor_ = val - TIC_RATIO__ * largeTicHeight_;
        } else {
          xminor_ = val - TIC_GAP__;
        }
        xmajor_ = xminor_ - LABEL_RATIO__ * labelHeight_;
      } else {
        vertalign_ = SGLabel.TOP;
        if (minorLabelInterval_ == 0) {
          xminor_ = val;
        } else if (ticPosition_ == BOTH_SIDES || ticPosition_ == POSITIVE_SIDE) {
          xminor_ = val + TIC_RATIO__ * largeTicHeight_;
        } else {
          xminor_ = val + TIC_GAP__;
        }
        xmajor_ = xminor_ + LABEL_RATIO__ * labelHeight_;
      }
    }
  }

  protected void drawMinorLabel(Graphics g, double val, GeoDate time) {
    SGLabel label;
    Color saved = g.getColor();
    if (orientation_ == Axis.HORIZONTAL) {
      label =
          new SGLabel("minor", time.toString(minorLabelFormat_), new Point2D.Double(val, yminor_));
      label.setOrientation(SGLabel.HORIZONTAL);
    } else {
      label =
          new SGLabel("minor", time.toString(minorLabelFormat_), new Point2D.Double(xminor_, val));
      label.setOrientation(SGLabel.VERTICAL);
    }
    label.setAlign(vertalign_, SGLabel.CENTER);
    label.setFont(labelFont_);
    label.setColor(labelColor_);
    label.setHeightP(labelHeight_);
    label.setLayer(graph_.getLayer());
    try {
      label.draw(g);
    } catch (LayerNotFoundException e) {
    }
    g.setColor(saved);
  }

  protected void drawMajorLabel(Graphics g, double val, GeoDate time) {
    Color saved = g.getColor();
    SGLabel label;
    if (orientation_ == Axis.HORIZONTAL) {
      label =
          new SGLabel("major", time.toString(majorLabelFormat_), new Point2D.Double(val, ymajor_));
      label.setOrientation(SGLabel.HORIZONTAL);
    } else {
      label =
          new SGLabel("major", time.toString(majorLabelFormat_), new Point2D.Double(xmajor_, val));
      label.setOrientation(SGLabel.VERTICAL);
    }
    label.setAlign(vertalign_, SGLabel.CENTER);
    label.setFont(labelFont_);
    label.setColor(labelColor_);
    label.setHeightP(MAJOR_LABEL_RATIO__ * labelHeight_);
    label.setLayer(graph_.getLayer());
    try {
      label.draw(g);
    } catch (LayerNotFoundException e) {
    }
    g.setColor(saved);
  }

  //
  /** Default contructor. */
  public TimeAxis(int style) {
    this("", style);
  }

  /**
   * TimeAxis constructor.
   *
   * @param id axis identifier
   */
  public TimeAxis(String id, int style) {
    super(id);
    minorLabelInterval_ = 2;
    majorLabelInterval_ = 1;
    numSmallTics_ = 0;
    space_ = false;
    axisStyle_ = style;
    //
    if (axisStyle_ == AUTO || axisStyle_ == MONTH_YEAR) {
      txt_ = new MonthYearAxis();
    } else if (axisStyle_ == YEAR_DECADE) {
      txt_ = new YearDecadeAxis();
    } else if (axisStyle_ == DAY_MONTH) {
      txt_ = new DayMonthAxis();
    } else if (axisStyle_ == HOUR_DAY) {
      txt_ = new HourDayAxis();
    } else {
      txt_ = new MinuteHourAxis();
    }
    minorLabelFormat_ = txt_.getDefaultMinorLabelFormat();
    majorLabelFormat_ = txt_.getDefaultMajorLabelFormat();
    minorLabelInterval_ = txt_.getDefaultMinorLabelInterval();
    majorLabelInterval_ = txt_.getDefaultMajorLabelInterval();
    numSmallTics_ = txt_.getDefaultNumSmallTics();
    //
    largeTicHeight_ = defaultLargeTicHeight__;
    smallTicHeight_ = defaultSmallTicHeight__;
    ticPosition_ = defaultTicPosition__;
    labelPosition_ = defaultLabelPosition__;
    labelHeight_ = defaultLabelHeight__;
    tRange_ = null;
    tLocation_ = null;
  }

  @Override
  public Axis copy() {
    TimeAxis newAxis;
    try {
      newAxis = (TimeAxis) clone();
    } catch (CloneNotSupportedException e) {
      newAxis = new TimeAxis(getStyle());
    }
    //
    // remove registered axes and transforms
    //
    newAxis.registeredAxes_ = new ArrayList<>();
    newAxis.registeredTransforms_ = new ArrayList<>();
    //
    return newAxis;
  }

  /**
   * Set the minor and major label formats.
   *
   * @param minor minor label format
   * @param major major label format
   */
  public void setLabelFormat(String minor, String major) {
    if (minorLabelFormat_ == null
        || majorLabelFormat_ == null
        || !minorLabelFormat_.equals(minor)
        || !majorLabelFormat_.equals(major)) {

      minorLabelFormat_ = minor;
      majorLabelFormat_ = major;
      modified("TimeAxis: setLabelFormat()");
    }
  }

  /**
   * Set the minor label format.
   *
   * @param minor minor label format
   */
  public void setMinorLabelFormat(String minor) {
    if (minorLabelFormat_ == null || !minorLabelFormat_.equals(minor)) {
      minorLabelFormat_ = minor;
      modified("TimeAxis: setMinorLabelFormat()");
    }
  }

  /**
   * Set the major label format.
   *
   * @param major major label format
   */
  public void setMajorLabelFormat(String major) {
    if (majorLabelFormat_ == null || !majorLabelFormat_.equals(major)) {
      majorLabelFormat_ = major;
      modified("TimeAxis: setMajorLabelFormat()");
    }
  }

  /**
   * Get the minor label format.
   *
   * @return minor label format
   */
  public String getMinorLabelFormat() {
    return minorLabelFormat_;
  }

  /**
   * Get the major label format.
   *
   * @return major label format
   */
  public String getMajorLabelFormat() {
    return majorLabelFormat_;
  }

  /**
   * Set the minor and major label intervals.
   *
   * @param minor minor label interval
   * @param major major label interval
   */
  public void setLabelInterval(int minor, int major) {
    if (minorLabelInterval_ != minor || majorLabelInterval_ != major) {
      minorLabelInterval_ = minor;
      majorLabelInterval_ = major;
      modified("TimeAxis: setLabelInterval()");
    }
  }

  /**
   * Set the minor label interval.
   *
   * @param minor minor label interval
   */
  public void setMinorLabelInterval(int minor) {
    if (minorLabelInterval_ != minor) {
      minorLabelInterval_ = minor;
      modified("TimeAxis: setMinorLabelInterval()");
    }
  }

  /**
   * Set the major label interval.
   *
   * @param major major label interval
   */
  public void setMajorLabelInterval(int major) {
    if (majorLabelInterval_ != major) {
      majorLabelInterval_ = major;
      modified("TimeAxis: setMajorLabelInterval()");
    }
  }

  /**
   * Get the minor label interval.
   *
   * @return minor label interval
   */
  public int getMinorLabelInterval() {
    return minorLabelInterval_;
  }

  /**
   * Get the major label interval.
   *
   * @return major label interval
   */
  public int getMajorLabelInterval() {
    return majorLabelInterval_;
  }

  /**
   * Set the time axis style.
   *
   * @param style new time axis style
   */
  public void setStyle(int style) {
    if (axisStyle_ != style) {
      axisStyle_ = style;
      if (axisStyle_ == AUTO && tRange_ != null) {
        setAuto();
      }
      modified("TimeAxis: setStyle()");
    }
  }

  /**
   * Get the time axis style.
   *
   * @return time axis style
   */
  public int getStyle() {
    return axisStyle_;
  }

  /**
   * Set the user range to draw the axis. Registered Axes and Transforms will be updated.
   *
   * @param tr TimeRange of axis.
   */
  public void setRangeU(TimeRange tr) {
    if (tRange_ == null || !tRange_.equals(tr)) {
      tRange_ = tr;
      if (axisStyle_ == AUTO) {
        setAuto();
      }
      updateRegisteredAxes();
      updateRegisteredTransforms();
      modified("TimeAxis: setRangeU()");
    }
  }

  /**
   * Get the time range of the axis.
   *
   * @return TimeRange of axis
   */
  public TimeRange getTimeRangeU() {
    return tRange_;
  }

  @Override
  public void setRangeU(SoTRange tr) {
    setRangeU(
        new TimeRange(
            tr.getStart().getLongTime(), tr.getEnd().getLongTime(), tr.getDelta().getLongTime()));
  }

  @Override
  public SoTRange getSoTRangeU() {
    return new SoTRange.Time(tRange_);
  }

  /**
   * Set the origin in user units of the axis.
   *
   * @param tp origin of axis in user units
   */
  public void setLocationU(TimePoint tp) {
    if (tLocation_ == null || !tLocation_.equals(tp)) {
      tLocation_ = tp;
      modified("TimeAxis: setLocationU()");
    }
  }

  @Override
  public void setLocationU(SoTPoint tp) {
    double x;
    long t;
    if (tp.isXTime()) {
      t = tp.getX().getLongTime();
      x = ((SoTValue.Double) tp.getY()).getValue();
    } else {
      t = tp.getY().getLongTime();
      x = ((SoTValue.Double) tp.getX()).getValue();
    }
    setLocationU(new TimePoint(x, new GeoDate(t)));
  }

  /** Returns origin as a <code>SoTPoint</code>. */
  @Override
  public SoTPoint getSoTLocationU() {
    if (orientation_ == HORIZONTAL) {
      return new SoTPoint(tLocation_.t, tLocation_.x);
    } else {
      return new SoTPoint(tLocation_.x, tLocation_.t);
    }
  }

  /**
   * Get the origin in user units.
   *
   * @return origin in user units
   */
  public TimePoint getLocationU() {
    return tLocation_;
  }

  //
  @Override
  public Rectangle getBounds() {
    double xp, yp, ymin, ymax, xmin, xmax;
    int xd, yd, width, height, x, y;
    if (orientation_ == Axis.HORIZONTAL) {
      //
      yp = graph_.getYUtoP(tLocation_.x);

      setupDraw(yp);

      xd = graph_.getXUtoD(tRange_.start);
      width = graph_.getXUtoD(tRange_.end) - xd;
      x = xd;
      ymax = yp;
      ymin = yp;
      if (labelPosition_ == POSITIVE_SIDE) {
        ymax = ymajor_ + MAJOR_LABEL_RATIO__ * labelHeight_;
        if (ticPosition_ == BOTH_SIDES || ticPosition_ == NEGATIVE_SIDE) {
          ymin = ymin - 1.3 * largeTicHeight_;
        }
      } else {
        ymin = ymajor_ - MAJOR_LABEL_RATIO__ * labelHeight_;
        if (ticPosition_ == BOTH_SIDES || ticPosition_ == POSITIVE_SIDE) {
          ymax = ymax + 1.3 * largeTicHeight_;
        }
      }
      y = graph_.getLayer().getYPtoD(ymax);
      height = graph_.getLayer().getYPtoD(ymin) - y;
    } else {
      xp = graph_.getXUtoP(tLocation_.x);

      setupDraw(xp);
      yd = graph_.getYUtoD(tRange_.start);
      y = graph_.getYUtoD(tRange_.end);
      height = yd - y;
      xmin = xp;
      xmax = xp;
      if (labelPosition_ == POSITIVE_SIDE) {
        xmax = xmajor_ + MAJOR_LABEL_RATIO__ * labelHeight_;
        if (ticPosition_ == BOTH_SIDES || ticPosition_ == NEGATIVE_SIDE) {
          xmin = xmin - 1.3 * largeTicHeight_;
        }
      } else {
        xmin = xmajor_ - MAJOR_LABEL_RATIO__ * labelHeight_;
        if (ticPosition_ == BOTH_SIDES || ticPosition_ == POSITIVE_SIDE) {
          xmax = xmax + 1.3 * largeTicHeight_;
        }
      }
      x = graph_.getLayer().getXPtoD(xmin);
      width = graph_.getLayer().getXPtoD(xp) - x;
    }
    return new Rectangle(x, y, width, height);
  }

  public void setBounds(Rectangle r) {
    setBounds(r.x, r.y, r.width, r.height);
  }

  public void setBounds(int x, int y, int width, int height) {}

  @Override
  public void modified(String mess) {
    //    if(Debug.EVENT) System.out.println("TimeAxis: modified()");
    if (graph_ != null) graph_.modified(mess);
  }

  //
  @Override
  public void draw(Graphics g) {
    int xloc, yloc, xend, yend;
    int minor_val;
    int major_val, major_val_old;
    double xp, yp;
    double x, y;
    double xp_major_old, yp_major_old;
    boolean draw_minor, draw_major;
    boolean time_increasing;
    GeoDate major_time_old;
    GeoDate time_end;
    if (!visible_) return;
    //
    if (lineColor_ == null) {
      g.setColor(graph_.getLayer().getPane().getComponent().getForeground());
    } else {
      g.setColor(lineColor_);
    }
    //
    draw_minor = minorLabelInterval_ != 0 && labelPosition_ != NO_LABEL;
    draw_major = majorLabelInterval_ != 0 && labelPosition_ != NO_LABEL;
    //
    time_increasing = tRange_.end.after(tRange_.start);
    //
    GeoDate time = txt_.getStartTime(tRange_);
    if (time_increasing) {
      time_end = new GeoDate(tRange_.end);
    } else {
      time_end = new GeoDate(tRange_.start);
    }
    boolean aMajorDrawn = false; // bob added
    //
    if (orientation_ == Axis.HORIZONTAL) {
      yloc = graph_.getYUtoD(tLocation_.x);
      xloc = graph_.getXUtoD(tRange_.start);
      xend = graph_.getXUtoD(tRange_.end);
      g.drawLine(xloc, yloc, xend, yloc);
      //
      yp = graph_.getYUtoP(tLocation_.x);
      xp = graph_.getXUtoP(time);

      setupDraw(yp);
      major_val = txt_.getMajorValue(time);
      major_val_old = major_val;
      xp_major_old = xp;
      major_time_old = new GeoDate(time);
      //
      // System.out.println("  minor draw=" + draw_minor + " interval=" + minorLabelInterval_ +
      //                   "  major draw=" + draw_major + " interval=" + majorLabelInterval_);
      while (time.before(time_end)) {
        xp = graph_.getXUtoP(time);
        minor_val = txt_.getMinorValue(time);
        // System.out.println("  minor_val=" + minor_val + " %=" + minor_val%minorLabelInterval_);
        if (txt_.isStartOfMinor(time)) {
          drawThickXTic(g, xp, yp, 1.3f * largeTicHeight_);
        } else if (minor_val % minorLabelInterval_ == 0) {
          drawXTic(g, xp, yp, largeTicHeight_);
        } else {
          drawXTic(g, xp, yp, smallTicHeight_);
        }
        if (draw_minor && minor_val % minorLabelInterval_ == 0) {
          drawMinorLabel(g, xp, time);
        }
        major_val = txt_.getMajorValue(time);
        if (major_val != major_val_old) {
          if (draw_major && major_val_old % majorLabelInterval_ == 0) {
            x = (xp_major_old + xp) * 0.5;
            drawMajorLabel(g, x, major_time_old);
            aMajorDrawn = true;
          }
          xp_major_old = xp;
          major_val_old = major_val;
          major_time_old = new GeoDate(time);
        }
        time.increment(txt_.getIncrementValue(), txt_.getIncrementUnits());
      } // end of while
      if (draw_major && (!aMajorDrawn || major_val_old % majorLabelInterval_ == 0)) {
        GeoDate delta = time_end.subtract(major_time_old);
        if (!aMajorDrawn || txt_.isRoomForMajorLabel(delta)) {
          xp = graph_.getXUtoP(time_end);
          x = (xp_major_old + xp) * 0.5;
          drawMajorLabel(g, x, major_time_old);
        }
      }
    } else { // vertical axis
      xloc = graph_.getXUtoD(tLocation_.x);
      yloc = graph_.getYUtoD(tRange_.start);
      yend = graph_.getYUtoD(tRange_.end);
      g.drawLine(xloc, yloc, xloc, yend);
      //
      xp = graph_.getXUtoP(tLocation_.x);
      yp = graph_.getYUtoP(time);

      setupDraw(xp);

      major_val = txt_.getMajorValue(time);
      major_val_old = major_val;
      yp_major_old = yp;
      major_time_old = new GeoDate(time);
      //
      while (time.before(time_end)) {
        yp = graph_.getYUtoP(time);
        minor_val = txt_.getMinorValue(time);
        if (txt_.isStartOfMinor(time)) {
          drawThickYTic(g, xp, yp, 1.3f * largeTicHeight_);
        } else if (minor_val % minorLabelInterval_ == 0) {
          drawYTic(g, xp, yp, largeTicHeight_);
        } else {
          drawYTic(g, xp, yp, smallTicHeight_);
        }
        if (draw_minor && minor_val % minorLabelInterval_ == 0) {
          drawMinorLabel(g, yp, time);
        }
        major_val = txt_.getMajorValue(time);
        if (major_val != major_val_old) {
          if (draw_major && major_val_old % majorLabelInterval_ == 0) {
            y = (yp_major_old + yp) * 0.5;
            drawMajorLabel(g, y, major_time_old);
            aMajorDrawn = true;
          }
          yp_major_old = yp;
          major_val_old = major_val;
          major_time_old = new GeoDate(time);
        }
        time.increment(txt_.getIncrementValue(), txt_.getIncrementUnits());
      } // end of while
      if (draw_major && (!aMajorDrawn || major_val_old % majorLabelInterval_ == 0)) {
        GeoDate delta = time_end.subtract(major_time_old);
        if (!aMajorDrawn || txt_.isRoomForMajorLabel(delta)) {
          yp = graph_.getYUtoP(time_end);
          y = (yp_major_old + yp) * 0.5;
          drawMajorLabel(g, y, major_time_old);
        }
      }
    }
  }

  @Override
  public void setTitle(SGLabel title) {
    // Time axes don't use title_
    title_ = null;
  }

  @Override
  public String toString() { // bob added
    return "TimeAxis: " + txt_; // timeAxisStyle
  }
}
