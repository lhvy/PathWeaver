package edu.wpi.first.pathweaver;

import java.util.Map;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;

public class Waypoint {
  private Waypoint previousWaypoint = null;
  private Waypoint nextWaypoint = null;
  private final DoubleProperty x = new SimpleDoubleProperty();
  private final DoubleProperty y = new SimpleDoubleProperty();
  private boolean lockTangent;
  private Spline previousSpline = null;
  private Spline nextSpline = null;
  private final ObjectProperty<Point2D> tangent = new SimpleObjectProperty<>();

  private final Path path;
  public static Waypoint currentWaypoint = null;


  private final Line tangentLine;
  private Polygon icon;

  private static final double SIZE = 30.0;


  public Path getPath() {
    return path;
  }

  /**
   * Creates Waypoint object containing javafx circle.
   *
   * @param position      x and y coordinates in user set units
   * @param tangentVector tangent vector in user set units
   * @param fixedAngle    If the angle the of the waypoint should be fixed. Used for first and last waypoint
   * @param myPath        the path this waypoint belongs to
   */
  public Waypoint(Point2D position, Point2D tangentVector, boolean fixedAngle, Path myPath) {
    path = myPath;
    lockTangent = fixedAngle;
    setX(position.getX());
    setY(position.getY());
    icon = new Polygon();
    setupIcon();
    x.addListener(__ -> update());
    y.addListener(__ -> update());

    tangentLine = new Line();
    tangentLine.getStyleClass().add("tangent");
    tangentLine.startXProperty().bind(x);
    tangentLine.startYProperty().bind(y);
    tangent.set(tangentVector);
    tangentLine.endXProperty().bind(Bindings.createObjectBinding(() -> getTangent().getX() + getX(), tangent, x));
    tangentLine.endYProperty().bind(Bindings.createObjectBinding(() -> getTangent().getY() + getY(), tangent, y));

    setupDnd();
  }


  public void enableSubchildSelector(int i) {
    FxUtils.enableSubchildSelector(this.icon, i);
    getIcon().applyCss();
  }

  private void setupIcon() {
    icon = new Polygon(
            0.0, SIZE / 3,
            SIZE, 0.0,
            0.0, -SIZE / 3);
    double xOffset = (SIZE * 3D / 5D) / 16.5;
    icon.setLayoutX(-(icon.getLayoutBounds().getMaxX() + icon.getLayoutBounds().getMinX()) / 2 - xOffset);
    icon.setLayoutY(-(icon.getLayoutBounds().getMaxY() + icon.getLayoutBounds().getMinY()) / 2);

    icon.translateXProperty().bind(x);
    icon.translateYProperty().bind(y);
    FxUtils.applySubchildClasses(this.icon);
    this.icon.rotateProperty().bind(
            Bindings.createObjectBinding(() ->
                    getTangent() == null ? 0.0 : Math.toDegrees(Math.atan2(getTangent().getY(), getTangent().getX())),
                    tangent));
    icon.getStyleClass().add("waypoint");
  }

  private void setupDnd() {
    icon.setOnDragDetected(event -> {
      currentWaypoint = this;
      icon.startDragAndDrop(TransferMode.MOVE)
          .setContent(Map.of(DataFormats.WAYPOINT, "point"));
    });
    tangentLine.setOnDragDetected(event -> {
      currentWaypoint = this;
      tangentLine.startDragAndDrop(TransferMode.MOVE)
          .setContent(Map.of(DataFormats.CONTROL_VECTOR, "vector"));
    });
    tangentLine.setOnMouseClicked(event -> {
      resetOnDoubleClick(event);
    });
  }

  /**
   * Handles reseting point depending on the mouse event.
   *
   * @param event The mouse event that was triggered
   */
  public void resetOnDoubleClick(MouseEvent event) {
    if (event.getClickCount() == 2 && lockTangent) {
      lockTangent = false;
      update();
    }
  }

  public void lockTangent() {
    lockTangent = true;
  }

  /**
   * Updates the control points for the splines attached to this waypoint and to each of its neighbors.
   */
  public void update() {
    updateTheta();
    if (previousWaypoint != null) {
      previousWaypoint.updateTheta();
      getPreviousSpline().updateControlPoints();
      if (previousWaypoint.getPreviousSpline() != null) {
        previousWaypoint.getPreviousSpline().updateControlPoints();
      }
    }
    if (nextWaypoint != null) {
      nextWaypoint.updateTheta();
      getNextSpline().updateControlPoints();
      if (nextWaypoint.getNextSpline() != null) {
        nextWaypoint.getNextSpline().updateControlPoints();
      }
    }
  }

  /**
   * Forces Waypoint to recompute optimal theta value. Does nothing if lockTangent is true.
   */
  @SuppressWarnings("PMD.NcssCount")
  public void updateTheta() {
    if (lockTangent) {
      return;
    }
    if (previousWaypoint == null) {
      return;
    }
    if (nextWaypoint == null) {
      return;
    }

    Point2D p1 = new Point2D(previousWaypoint.getX(), previousWaypoint.getY());
    Point2D p2 = new Point2D(this.getX(), this.getY());
    Point2D p3 = new Point2D(nextWaypoint.getX(), nextWaypoint.getY());

    Point2D p1Scaled = new Point2D(0, 0);
    Point2D p2Scaled = p2.subtract(p1).multiply(1 / p3.distance(p1));
    Point2D p3Shifted = p3.subtract(p1);
    Point2D p3Scaled = p3Shifted.multiply(1 / p3.distance(p1)); // scale

    //refactor later
    // Point2D q = new Point2D(0, 0); // for reference
    Point2D r = new Point2D(p2Scaled.getX() * p3Scaled.getX() + p2Scaled.getY() * p3Scaled.getY(),

        -p2Scaled.getX() * p3Scaled.getY() + p2Scaled.getY() * p3Scaled.getX());
    // Point2D s = new Point2D(1, 0); // for reference

    double beta = 1 - 2 * r.getX();
    double gamma = Math.pow(4 * (r.getX() - Math.pow(r.distance(p1Scaled), 2)) - 3, 3) / 27;
    double lambda = Math.pow(-gamma, 1 / 6);

    double phi1 = Math.atan2(Math.sqrt(-gamma - Math.pow(beta, 2)), beta) / 3;
    double ur = lambda * Math.cos(phi1);
    double ui = lambda * Math.sin(phi1);
    double phi2 = Math.atan2(-Math.sqrt(-gamma - Math.pow(beta, 2)), beta) / 3;

    double zr = lambda * Math.cos(phi2);
    double zi = lambda * Math.sin(phi2);

    double t1 = 1.0 / 2 + ur + zr / 2;
    double t2 = 1.0 / 2 - (1.0 / 4) * (ur + zr + Math.sqrt(3) * (ui - zi));
    double t3 = 1.0 / 2 - (1.0 / 4) * (ur + zr - Math.sqrt(3) * (ui - zi));

    double t;
    if (t1 > 0 && t1 < 1) {
      t = t1;
    } else if (t2 > 0 && t2 < 1) {
      t = t2;
    } else {
      t = t3;
    }

    Point2D a1 = p2.subtract(p1).subtract(p3Shifted.multiply(t)).multiply(1 / (t * t - t));
    Point2D a2 = p3Shifted.subtract(a1);

    Point2D tangent = a1.multiply(2 * t).add(a2).multiply(1. / 3);
    this.tangent.set(tangent);
  }

  /**
   * Sets previous or nextSpline and binds the Spline to waypoints position.
   *
   * @param newSpline The spline to add
   * @param amFirst   True if this waypoint is the first point in the spline
   */
  public void addSpline(Spline newSpline, boolean amFirst) {
    if (amFirst) {
      nextSpline = newSpline;
      nextSpline.getCubic().startXProperty().bind(x);
      nextSpline.getCubic().startYProperty().bind(y);
      newSpline.setStart(this);
    }
    if (!amFirst) {
      previousSpline = newSpline;
      previousSpline.getCubic().endXProperty().bind(x);
      previousSpline.getCubic().endYProperty().bind(y);
      newSpline.setEnd(this);
    }
  }

  /**
   * Convenience function for math purposes.
   *
   * @param other The other Waypoint.
   *
   * @return The coordinates of this Waypoint relative to the coordinates of another Waypoint.
   */
  public Point2D relativeTo(Waypoint other) {
    return relativeTo(other.getCoords());
  }

  /**
   * Convenience function allowing us to obtain the position of this Waypoint relative to a Point2S.
   *
   * @param other The other Point2D.
   *
   * @return A Point2D representing the distance between this Watpoint and a given Point2D.
   */
  public Point2D relativeTo(Point2D other) {
    return new Point2D(this.getX() - other.getX(), this.getY() - other.getY());
  }

  public boolean isLockTangent() {
    return lockTangent;
  }

  public Line getTangentLine() {
    return tangentLine;
  }

  public Point2D getTangent() {
    return tangent.get();
  }

  public ObjectProperty<Point2D> tangentProperty() {
    return tangent;
  }

  public void setTangent(Point2D tangent) {
    this.tangent.set(tangent);
  }

  public Spline getPreviousSpline() {
    return previousSpline;
  }

  public Spline getNextSpline() {
    return nextSpline;
  }

  public Polygon getIcon() {
    return icon;
  }

  public double getX() {
    return x.get();
  }

  public DoubleProperty xProperty() {
    return x;
  }

  public void setX(double x) {
    this.x.set(x);
  }

  public double getY() {
    return y.get();
  }

  public DoubleProperty yProperty() {
    return y;
  }

  public void setY(double y) {
    this.y.set(y);
  }

  public Point2D getCoords() {
    return new Point2D(getX(), getY());
  }

  public void setCoords(Point2D newCoords) {
    setX(newCoords.getX());
    setY(newCoords.getY());
  }

  public Waypoint getPreviousWaypoint() {
    return previousWaypoint;
  }

  public void setPreviousWaypoint(Waypoint previousWaypoint) {
    this.previousWaypoint = previousWaypoint;
  }

  public Waypoint getNextWaypoint() {
    return nextWaypoint;
  }

  public void setNextWaypoint(Waypoint nextWaypoint) {
    this.nextWaypoint = nextWaypoint;
  }

}