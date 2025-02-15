/*
 *
 *
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *
 *
 */
package com.jetbrains.youtrack.db.internal.core.sql.functions.graph;

import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.metadata.function.Function;
import com.jetbrains.youtrack.db.api.record.Direction;
import com.jetbrains.youtrack.db.api.record.Vertex;
import com.jetbrains.youtrack.db.internal.core.sql.functions.math.SQLFunctionMathAbstract;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Abstract class to find paths between nodes using heuristic .
 */
public abstract class SQLFunctionHeuristicPathFinderAbstract extends SQLFunctionMathAbstract {

  public static final String PARAM_DIRECTION = "direction";
  public static final String PARAM_EDGE_TYPE_NAMES = "edgeTypeNames";
  public static final String PARAM_VERTEX_AXIS_NAMES = "vertexAxisNames";
  public static final String PARAM_PARALLEL = "parallel";
  public static final String PARAM_MAX_DEPTH = "maxDepth";
  public static final String PARAM_HEURISTIC_FORMULA = "heuristicFormula";
  public static final String PARAM_CUSTOM_HEURISTIC_FORMULA = "customHeuristicFormula";
  public static final String PARAM_D_FACTOR = "dFactor";
  public static final String PARAM_TIE_BREAKER = "tieBreaker";
  public static final String PARAM_EMPTY_IF_MAX_DEPTH = "emptyIfMaxDepth";
  protected static Random rnd = new Random();

  protected Boolean paramParallel = false;
  protected Boolean paramTieBreaker = true;
  protected Boolean paramEmptyIfMaxDepth = false;
  protected String[] paramEdgeTypeNames = new String[]{};
  protected String[] paramVertexAxisNames = new String[]{};
  protected Vertex paramSourceVertex;
  protected Vertex paramDestinationVertex;
  protected HeuristicFormula paramHeuristicFormula = HeuristicFormula.MANHATAN;
  protected Direction paramDirection = Direction.OUT;
  protected long paramMaxDepth = Long.MAX_VALUE;
  protected double paramDFactor = 1.0;
  protected String paramCustomHeuristicFormula = "";

  protected CommandContext context;
  protected List<Vertex> route = new LinkedList<Vertex>();
  protected static final float MIN = 0f;

  public SQLFunctionHeuristicPathFinderAbstract(
      final String iName, final int iMinParams, final int iMaxParams) {
    super(iName, iMinParams, iMaxParams);
  }

  protected boolean isVariableEdgeWeight() {
    return false;
  }

  protected abstract double getDistance(
      final Vertex node, final Vertex parent, final Vertex target);

  protected abstract double getHeuristicCost(
      final Vertex node, final Vertex parent, final Vertex target, CommandContext iContext);

  protected LinkedList<Vertex> getPath() {
    final LinkedList<Vertex> path = new LinkedList<Vertex>(route);
    return path;
  }

  protected Set<Vertex> getNeighbors(final Vertex node) {
    context.incrementVariable("getNeighbors");

    final Set<Vertex> neighbors = new HashSet<Vertex>();
    if (node != null) {
      for (Vertex v : node.getVertices(paramDirection, paramEdgeTypeNames)) {
        final Vertex ov = v;
        if (ov != null) {
          neighbors.add(ov);
        }
      }
    }
    return neighbors;
  }

  // obtains from http://theory.stanford.edu/~amitp/GameProgramming/Heuristics.html
  protected double getSimpleHeuristicCost(double x, double g, double dFactor) {
    double dx = Math.abs(x - g);
    return dFactor * (dx);
  }

  // obtains from http://theory.stanford.edu/~amitp/GameProgramming/Heuristics.html
  protected double getManhatanHeuristicCost(
      double x, double y, double gx, double gy, double dFactor) {
    double dx = Math.abs(x - gx);
    double dy = Math.abs(y - gy);
    return dFactor * (dx + dy);
  }

  protected double getMaxAxisHeuristicCost(
      double x, double y, double gx, double gy, double dFactor) {
    double dx = Math.abs(x - gx);
    double dy = Math.abs(y - gy);
    return dFactor * Math.max(dx, dy);
  }

  // obtains from http://theory.stanford.edu/~amitp/GameProgramming/Heuristics.html
  protected double getDiagonalHeuristicCost(
      double x, double y, double gx, double gy, double dFactor) {
    double dx = Math.abs(x - gx);
    double dy = Math.abs(y - gy);
    double hDiagonal = Math.min(dx, dy);
    double hStraight = dx + dy;
    return (dFactor * 2) * hDiagonal + dFactor * (hStraight - 2 * hDiagonal);
  }

  // obtains from http://theory.stanford.edu/~amitp/GameProgramming/Heuristics.html
  protected double getEuclideanHeuristicCost(
      double x, double y, double gx, double gy, double dFactor) {
    double dx = Math.abs(x - gx);
    double dy = Math.abs(y - gy);

    return (dFactor * Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2)));
  }

  protected double getEuclideanNoSQRHeuristicCost(
      double x, double y, double gx, double gy, double dFactor) {
    double dx = Math.abs(x - gx);
    double dy = Math.abs(y - gy);

    return (dFactor * (Math.pow(dx, 2) + Math.pow(dy, 2)));
  }

  protected double getCustomHeuristicCost(
      final String functionName,
      final String[] vertextAxisNames,
      final Vertex start,
      final Vertex goal,
      final Vertex current,
      final Vertex parent,
      final long depth,
      double dFactor,
      CommandContext ctx) {

    double heuristic = 0.0;

    Function func = ctx.getDatabase().getMetadata().getFunctionLibrary().getFunction(functionName);
    Object fValue =
        func.executeInContext(
            context, vertextAxisNames, start, goal, current, parent, depth, dFactor);
    if (fValue != null && fValue instanceof Number) {
      heuristic = doubleOrDefault(fValue, heuristic);
    }
    return heuristic;
  }

  // obtains from http://theory.stanford.edu/~amitp/GameProgramming/Heuristics.html
  protected double getTieBreakingHeuristicCost(
      double x, double y, double sx, double sy, double gx, double gy, double heuristic) {
    double dx1 = x - gx;
    double dy1 = y - gy;
    double dx2 = sx - gx;
    double dy2 = sy - gy;
    double cross = Math.abs(dx1 * dy2 - dx2 * dy1);
    heuristic += (cross * 0.0001);
    return heuristic;
  }

  protected double getTieBreakingRandomHeuristicCost(
      double x, double y, double sx, double sy, double gx, double gy, double heuristic) {
    double dx1 = x - gx;
    double dy1 = y - gy;
    double dx2 = sx - gx;
    double dy2 = sy - gy;
    double cross = Math.abs(dx1 * dy2 - dx2 * dy1) + rnd.nextFloat();
    heuristic += (cross * heuristic);
    return heuristic;
  }

  protected double getManhatanHeuristicCost(
      final String[] axisNames,
      final Map<String, Double> slist,
      final Map<String, Double> clist,
      final Map<String, Double> plist,
      final Map<String, Double> glist,
      long depth,
      double dFactor) {
    Double heuristic = 0.0;
    double res = 0.0;
    for (String str : axisNames) {
      res +=
          Math.abs(
              (clist.get(str) != null ? clist.get(str) : 0.0)
                  - (glist.get(str) != null ? glist.get(str) : 0.0));
    }
    heuristic = dFactor * res;
    return heuristic;
  }

  protected double getMaxAxisHeuristicCost(
      final String[] axisNames,
      final Map<String, Double> slist,
      final Map<String, Double> clist,
      final Map<String, Double> plist,
      final Map<String, Double> glist,
      long depth,
      double dFactor) {
    Double heuristic = 0.0;
    double res = 0.0;
    for (String str : axisNames) {
      res =
          Math.max(
              Math.abs(
                  (clist.get(str) != null ? clist.get(str) : 0.0)
                      - (glist.get(str) != null ? glist.get(str) : 0.0)),
              res);
    }
    heuristic = dFactor * res;
    return heuristic;
  }

  protected double getDiagonalHeuristicCost(
      final String[] axisNames,
      final Map<String, Double> slist,
      final Map<String, Double> clist,
      final Map<String, Double> plist,
      final Map<String, Double> glist,
      long depth,
      double dFactor) {

    Double heuristic = 0.0;
    double hDiagonal = 0.0;
    double hStraight = 0.0;
    for (String str : axisNames) {
      hDiagonal =
          Math.min(
              Math.abs(
                  (clist.get(str) != null ? clist.get(str) : 0.0)
                      - (glist.get(str) != null ? glist.get(str) : 0.0)),
              hDiagonal);
      hStraight +=
          Math.abs(
              (clist.get(str) != null ? clist.get(str) : 0.0)
                  - (glist.get(str) != null ? glist.get(str) : 0.0));
    }
    heuristic = (dFactor * 2) * hDiagonal + dFactor * (hStraight - 2 * hDiagonal);
    return heuristic;
  }

  protected double getEuclideanHeuristicCost(
      final String[] axisNames,
      final Map<String, Double> slist,
      final Map<String, Double> clist,
      final Map<String, Double> plist,
      final Map<String, Double> glist,
      long depth,
      double dFactor) {
    Double heuristic = 0.0;
    double res = 0.0;
    for (String str : axisNames) {
      res +=
          Math.pow(
              Math.abs(
                  (clist.get(str) != null ? clist.get(str) : 0.0)
                      - (glist.get(str) != null ? glist.get(str) : 0.0)),
              2);
    }
    heuristic = Math.sqrt(res);
    return heuristic;
  }

  protected double getEuclideanNoSQRHeuristicCost(
      final String[] axisNames,
      final Map<String, Double> slist,
      final Map<String, Double> clist,
      final Map<String, Double> plist,
      final Map<String, Double> glist,
      long depth,
      double dFactor) {
    Double heuristic = 0.0;
    double res = 0.0;
    for (String str : axisNames) {
      res +=
          Math.pow(
              Math.abs(
                  (clist.get(str) != null ? clist.get(str) : 0.0)
                      - (glist.get(str) != null ? glist.get(str) : 0.0)),
              2);
    }
    heuristic = dFactor * res;
    return heuristic;
  }

  protected double getTieBreakingHeuristicCost(
      final String[] axisNames,
      final Map<String, Double> slist,
      final Map<String, Double> clist,
      final Map<String, Double> plist,
      final Map<String, Double> glist,
      long depth,
      double heuristic) {

    double res = 0.0;
    for (String str : axisNames) {
      res +=
          Math.abs(
              (clist.get(str) != null ? clist.get(str) : 0.0)
                  - (glist.get(str) != null ? glist.get(str) : 0.0));
    }
    double cross = res;
    heuristic += (cross * 0.0001);
    return heuristic;
  }

  protected String[] stringArray(Object fromObject) {
    if (fromObject == null) {
      return new String[]{};
    }
    if (fromObject instanceof String) {
      String[] arr = fromObject.toString().replace("},{", " ,").split(",");
      return (arr);
    }
    if (fromObject instanceof Object) {
      return ((String[]) fromObject);
    }

    return new String[]{};
  }

  protected Boolean booleanOrDefault(Object fromObject, boolean defaultValue) {
    if (fromObject == null) {
      return defaultValue;
    }
    if (fromObject instanceof Boolean) {
      return (Boolean) fromObject;
    }
    if (fromObject instanceof String) {
      return Boolean.parseBoolean((String) fromObject);
    }
    return defaultValue;
  }

  protected String stringOrDefault(Object fromObject, String defaultValue) {
    if (fromObject == null) {
      return defaultValue;
    }
    return (String) fromObject;
  }

  protected Integer integerOrDefault(Object fromObject, int defaultValue) {
    if (fromObject == null) {
      return defaultValue;
    }
    if (fromObject instanceof Number) {
      return ((Number) fromObject).intValue();
    }
    if (fromObject instanceof String) {
      try {
        return Integer.parseInt(fromObject.toString());
      } catch (NumberFormatException ignore) {
      }
    }
    return defaultValue;
  }

  protected Long longOrDefault(Object fromObject, long defaultValue) {
    if (fromObject == null) {
      return defaultValue;
    }
    if (fromObject instanceof Number) {
      return ((Number) fromObject).longValue();
    }
    if (fromObject instanceof String) {
      try {
        return Long.parseLong(fromObject.toString());
      } catch (NumberFormatException ignore) {
      }
    }
    return defaultValue;
  }

  protected Double doubleOrDefault(Object fromObject, double defaultValue) {
    if (fromObject == null) {
      return defaultValue;
    }
    if (fromObject instanceof Number) {
      return ((Number) fromObject).doubleValue();
    }
    if (fromObject instanceof String) {
      try {
        return Double.parseDouble(fromObject.toString());
      } catch (NumberFormatException ignore) {
      }
    }
    return defaultValue;
  }
}
