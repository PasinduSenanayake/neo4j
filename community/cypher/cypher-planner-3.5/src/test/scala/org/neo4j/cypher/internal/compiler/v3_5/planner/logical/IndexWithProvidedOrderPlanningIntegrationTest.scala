/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v3_5.planner.logical

import org.neo4j.cypher.internal.compiler.v3_5.planner.LogicalPlanningTestSupport2
import org.neo4j.cypher.internal.ir.v3_5.RegularPlannerQuery
import org.neo4j.cypher.internal.planner.v3_5.spi.AscIndexOrder
import org.neo4j.cypher.internal.v3_5.logical.plans.{Skip => SkipPlan, _}
import org.opencypher.v9_0.ast._
import org.opencypher.v9_0.expressions._
import org.opencypher.v9_0.util._
import org.opencypher.v9_0.util.test_helpers.CypherFunSuite

class IndexWithProvidedOrderPlanningIntegrationTest extends CypherFunSuite with LogicalPlanningTestSupport2 with AstConstructionTestSupport {

  test("Order by index backed property should plan with provided order") {
    val plan = new given {
      indexOn("Awesome", "prop").providesOrder(AscIndexOrder)
    } getLogicalPlanFor "MATCH (n:Awesome) WHERE n.prop > 'foo' RETURN n.prop ORDER BY n.prop"

    plan._2 should equal(
      Projection(
        Projection(
          IndexSeek("n:Awesome(prop > 'foo')", indexOrder = IndexOrderAscending),
          Map("  FRESHID48" -> Property(Variable("n")(pos), PropertyKeyName("prop")(pos))(pos))),
        Map("n.prop" -> Variable("  FRESHID48")(pos)))
    )
  }

  test("Order by index backed property should plan sort if index does not provide order") {
    val plan = new given {
      indexOn("Awesome", "prop")
    } getLogicalPlanFor "MATCH (n:Awesome) WHERE n.prop > 'foo' RETURN n.prop ORDER BY n.prop"

    plan._2 should equal(
      Projection(
        Sort(
          Projection(
            IndexSeek("n:Awesome(prop > 'foo')", indexOrder = IndexOrderNone),
            Map("  FRESHID48" -> Property(Variable("n")(pos), PropertyKeyName("prop")(pos))(pos))),
          Seq(Ascending("  FRESHID48"))),
        Map("n.prop" -> Variable("  FRESHID48")(pos)))
    )
  }

  test("Order by index backed property DESCENDING (unsupported) should plan sort") {
    val plan = new given {
      indexOn("Awesome", "prop").providesOrder(AscIndexOrder)
    } getLogicalPlanFor "MATCH (n:Awesome) WHERE n.prop > 'foo' RETURN n.prop ORDER BY n.prop DESC"

    plan._2 should equal(
      Projection(
        Sort(
          Projection(
            IndexSeek("n:Awesome(prop > 'foo')", indexOrder = IndexOrderAscending),
            Map("  FRESHID48" -> Property(Variable("n")(pos), PropertyKeyName("prop")(pos))(pos))),
          Seq(Descending("  FRESHID48"))),
        Map("n.prop" -> Variable("  FRESHID48")(pos)))
    )
  }

  test("Order by index backed property renamed in an earlier WITH") {
    val plan = new given {
      indexOn("Awesome", "prop").providesOrder(AscIndexOrder)
    } getLogicalPlanFor
      """MATCH (n:Awesome) WHERE n.prop > 'foo'
        |WITH n AS nnn
        |MATCH (m)-[r]->(nnn)
        |RETURN nnn.prop ORDER BY nnn.prop""".stripMargin

    plan._2 should equal(
      Projection(
        Projection(
          Expand(
            Projection(
              IndexSeek("n:Awesome(prop > 'foo')", indexOrder = IndexOrderAscending),
              Map("nnn" -> Variable("n")(pos))),
            "nnn", SemanticDirection.INCOMING, Seq.empty, "m", "r"),
          Map("  FRESHID85" -> Property(Variable("nnn")(pos), PropertyKeyName("prop")(pos))(pos))),
        Map("nnn.prop" -> Variable("  FRESHID85")(pos)))
    )
  }

  test("Order by index backed property renamed in same return") {
    val plan = new given {
      indexOn("Awesome", "prop").providesOrder(AscIndexOrder)
    } getLogicalPlanFor
      """MATCH (n:Awesome) WHERE n.prop > 'foo'
        |RETURN n AS m ORDER BY m.prop""".stripMargin

    plan._2 should equal(
      Projection(
        Projection(
          IndexSeek("n:Awesome(prop > 'foo')", indexOrder = IndexOrderAscending),
          Map("  FRESHID46" -> Variable("n")(pos))),
        Map("m" -> Variable("  FRESHID46")(pos)))
    )
  }

  test("Cannot order by index when ordering is on same property name, but different node") {
    val plan = new given {
      indexOn("Awesome", "prop").providesOrder(AscIndexOrder)
    } getLogicalPlanFor "MATCH (m:Awesome), (n:Awesome) WHERE n.prop > 'foo' RETURN m.prop ORDER BY m.prop"

    plan._2 should equal(
      Projection(
        Sort(
          Projection(
            CartesianProduct(
              IndexSeek("n:Awesome(prop > 'foo')", indexOrder = IndexOrderAscending),
              NodeByLabelScan("m", LabelName("Awesome")(pos), Set.empty)),
            Map("  FRESHID61" -> Property(Variable("m")(pos), PropertyKeyName("prop")(pos))(pos))),
          Seq(Ascending("  FRESHID61"))),
        Map("m.prop" -> Variable("  FRESHID61")(pos)))
    )
  }

  test("Order by index backed property should plan with provided order (starts with scan)") {
    val plan = new given {
      indexOn("Awesome", "prop").providesOrder(AscIndexOrder)
    } getLogicalPlanFor "MATCH (n:Awesome) WHERE n.prop STARTS WITH 'foo' RETURN n.prop ORDER BY n.prop"

    plan._2 should equal(

      Projection(
        Projection(
          IndexSeek("n:Awesome(prop STARTS WITH 'foo')", indexOrder = IndexOrderAscending),
          Map("  FRESHID58" -> Property(Variable("n")(pos), PropertyKeyName("prop")(pos))(pos))),
        Map("n.prop" -> Variable("  FRESHID58")(pos)))
    )
  }

  // This is supported because internally all kernel indexes which support ordering will just scan and filter to serve contains
  test("Order by index backed property should plan with provided order (contains scan)") {
    val plan = new given {
      indexOn("Awesome", "prop").providesOrder(AscIndexOrder)
    } getLogicalPlanFor "MATCH (n:Awesome) WHERE n.prop CONTAINS 'foo' RETURN n.prop ORDER BY n.prop"

    plan._2 should equal(
      Projection(
        Projection(
          IndexSeek("n:Awesome(prop CONTAINS 'foo')", indexOrder = IndexOrderAscending),
          Map("  FRESHID55" -> Property(Variable("n")(pos), PropertyKeyName("prop")(pos))(pos))),
        Map("n.prop" -> Variable("  FRESHID55")(pos)))
    )
  }

  // This is supported because internally all kernel indexes which support ordering will just scan and filter to serve ends with
  test("Order by index backed property should plan with provided order (ends with scan)") {
    val plan = new given {
      indexOn("Awesome", "prop").providesOrder(AscIndexOrder)
    } getLogicalPlanFor "MATCH (n:Awesome) WHERE n.prop ENDS WITH 'foo' RETURN n.prop ORDER BY n.prop"

    plan._2 should equal(
      Projection(
        Projection(
          IndexSeek("n:Awesome(prop ENDS WITH 'foo')", indexOrder = IndexOrderAscending),
          Map("  FRESHID56" -> Property(Variable("n")(pos), PropertyKeyName("prop")(pos))(pos))),
        Map("n.prop" -> Variable("  FRESHID56")(pos)))
    )
  }

  test("Order by index backed property should plan with provided order (scan)") {
    val plan = new given {
      indexOn("Awesome", "prop").providesOrder(AscIndexOrder)
    } getLogicalPlanFor "MATCH (n:Awesome) WHERE EXISTS(n.prop) RETURN n.prop ORDER BY n.prop"

    plan._2 should equal(

      Projection(
        Projection(
          IndexSeek("n:Awesome(prop)", indexOrder = IndexOrderAscending),
          Map("  FRESHID48" -> Property(Variable("n")(pos), PropertyKeyName("prop")(pos))(pos))),
        Map("n.prop" -> Variable("  FRESHID48")(pos)))
    )
  }

  test("Order by index backed property in a plan with an Apply") {
    val plan = new given {
      indexOn("A", "prop").providesOrder(AscIndexOrder)
      indexOn("B", "prop").providesOrder(AscIndexOrder)
    } getLogicalPlanFor "MATCH (a:A), (b:B) WHERE a.prop > 'foo' AND a.prop = b.prop RETURN a.prop ORDER BY a.prop"

    plan._2 should equal(

      Projection(
        Projection(
          Apply(
            IndexSeek("a:A(prop > 'foo')", indexOrder = IndexOrderAscending),
            IndexSeek("b:B(prop = ???)",
              indexOrder = IndexOrderAscending,
              paramExpr = Some(prop("a", "prop")),
              labelId = 1,
              argumentIds = Set("a"))
          ),
          Map("  FRESHID69" -> Property(Variable("a")(pos), PropertyKeyName("prop")(pos))(pos))),
        Map("a.prop" -> Variable("  FRESHID69")(pos)))
    )
  }

  test("Order by index backed properties in a plan with an Apply needs Sort if RHS order required") {
    val plan = new given {
      indexOn("A", "prop").providesOrder(AscIndexOrder)
      indexOn("B", "prop").providesOrder(AscIndexOrder)
      // This query is very fragile in the sense that the slightest modification will result in a stupid plan
    } getLogicalPlanFor "MATCH (a:A), (b:B) WHERE a.prop STARTS WITH 'foo' AND b.prop > a.prop RETURN a.prop, b.prop ORDER BY a.prop, b.prop"

    plan._2 should equal(
      Projection(
        Sort(
          Projection(
            Apply(
              IndexSeek("a:A(prop STARTS WITH 'foo')", indexOrder = IndexOrderAscending),
              IndexSeek("b:B(prop > ???)",
                indexOrder = IndexOrderAscending,
                paramExpr = Some(prop("a", "prop")),
                labelId = 1,
                argumentIds = Set("a"))
            ),
            Map("  FRESHID79" -> Property(Variable("a")(pos), PropertyKeyName("prop")(pos))(pos), "  FRESHID87" -> Property(Variable("b")(pos), PropertyKeyName("prop")(pos))(pos))),
          Seq(Ascending("  FRESHID79"), Ascending("  FRESHID87"))),
        Map("a.prop" -> Variable("  FRESHID79")(pos), "b.prop" -> Variable("  FRESHID87")(pos)))
    )
  }

  test("Order by index backed property in a plan with an renaming Projection") {
    val plan = new given {
      indexOn("A", "prop").providesOrder(AscIndexOrder)
    } getLogicalPlanFor "MATCH (a:A) WHERE a.prop > 'foo' WITH a.prop AS theProp, 1 AS x RETURN theProp ORDER BY theProp"

    plan._2 should equal(

      Projection(
        Projection(
          Projection(
            IndexSeek("a:A(prop > 'foo')", indexOrder = IndexOrderAscending),
            Map("  theProp@48" -> Property(Variable("a")(pos), PropertyKeyName("prop")(pos))(pos), "x" -> SignedDecimalIntegerLiteral("1")(pos))),
          Map("  FRESHID71" -> varFor("  theProp@48"))),
        Map("theProp" -> Variable("  FRESHID71")(pos)))
    )
  }

  test("Order by index backed property in a plan with an aggregation and an expand") {
    val plan = new given {
      indexOn("A", "prop").providesOrder(AscIndexOrder)
      cardinality = mapCardinality {
        // Force the planner to start at a
        case RegularPlannerQuery(queryGraph, _, _, _) if queryGraph.patternNodes == Set("a") => 100.0
        case RegularPlannerQuery(queryGraph, _, _, _) if queryGraph.patternNodes == Set("b") => 2000.0
      }
    } getLogicalPlanFor "MATCH (a:A)-[r]->(b) WHERE a.prop > 'foo' RETURN a.prop, count(b) ORDER BY a.prop"

    plan._2 should equal(

      Projection(
        Aggregation(
          Expand(
            IndexSeek("a:A(prop > 'foo')", indexOrder = IndexOrderAscending),
            "a", SemanticDirection.OUTGOING, Seq.empty, "b", "r"),
          Map("  FRESHID51" -> prop("a", "prop")), Map("  FRESHID57" -> FunctionInvocation(Namespace(List())(pos),FunctionName("count")(pos), distinct = false,Vector(varFor("b")))(pos))),
        Map("a.prop" -> Variable("  FRESHID51")(pos), "count(b)" -> Variable("  FRESHID57")(pos)))
    )
  }

  test("Order by index backed property in a plan with a distinct") {
    val plan = new given {
      indexOn("A", "prop").providesOrder(AscIndexOrder)
      cardinality = mapCardinality {
        // Force the planner to start at a
        case RegularPlannerQuery(queryGraph, _, _, _) if queryGraph.patternNodes == Set("a") => 100.0
        case RegularPlannerQuery(queryGraph, _, _, _) if queryGraph.patternNodes == Set("b") => 2000.0
      }
    } getLogicalPlanFor "MATCH (a:A)-[r]->(b) WHERE a.prop > 'foo' RETURN DISTINCT a.prop ORDER BY a.prop"

    plan._2 should equal(

      Projection(
        Distinct(
          Expand(
            IndexSeek("a:A(prop > 'foo')", indexOrder = IndexOrderAscending),
            "a", SemanticDirection.OUTGOING, Seq.empty, "b", "r"),
          Map("  FRESHID60" -> prop("a", "prop"))),
        Map("a.prop" -> Variable("  FRESHID60")(pos)))
    )
  }

  test("Order by index backed property in a plan with a outer join") {
    val plan = new given {
      indexOn("A", "prop").providesOrder(AscIndexOrder)
      cardinality = mapCardinality {
        // Force the planner to start at b
        case RegularPlannerQuery(queryGraph, _, _, _) if queryGraph.patternNodes == Set("a", "b") => 100.0
        case RegularPlannerQuery(queryGraph, _, _, _) if queryGraph.patternNodes == Set("b") => 20.0
      }
    } getLogicalPlanFor "MATCH (b) OPTIONAL MATCH (a:A)-[r]->(b) USING JOIN ON b WHERE a.prop > 'foo' RETURN a.prop ORDER BY a.prop"

    plan._2 should equal(

      Projection(
        Projection(
          LeftOuterHashJoin(Set("b"),
            AllNodesScan("b", Set.empty),
            Expand(
              IndexSeek("a:A(prop > 'foo')", indexOrder = IndexOrderAscending),
              "a", SemanticDirection.OUTGOING, Seq.empty, "b", "r")),
          Map("  FRESHID86" -> prop("a", "prop"))),
        Map("a.prop" -> Variable("  FRESHID86")(pos)))
    )
  }

  test("Order by index backed property in a plan with a tail apply") {
    val plan = new given {
      indexOn("A", "prop").providesOrder(AscIndexOrder)
    } getLogicalPlanFor
      """MATCH (a:A) WHERE a.prop > 'foo' WITH a SKIP 0
        |MATCH (b)
        |RETURN a.prop, b ORDER BY a.prop""".stripMargin

    plan._2 should equal(
      Projection(
        Projection(
          Apply(
            SkipPlan(
              IndexSeek("a:A(prop > 'foo')", indexOrder = IndexOrderAscending),
              SignedDecimalIntegerLiteral("0")(pos)),
            AllNodesScan("  b@54", Set("a"))),
          Map("  FRESHID66" -> prop("a", "prop"), "  FRESHID72" -> varFor("  b@54"))),
        Map("a.prop" -> Variable("  FRESHID66")(pos), "b" -> Variable("  FRESHID72")(pos)))
    )
  }

}
