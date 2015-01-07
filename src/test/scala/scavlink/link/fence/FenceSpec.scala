package scavlink.link.fence

import scavlink.coord.Geo
import com.spatial4j.core.context.jts.JtsSpatialContext
import com.spatial4j.core.shape.impl.PointImpl
import org.scalatest.{Matchers, WordSpec}
import spire.implicits._
import spire.math.Interval

import scala.util.Try

class FenceSpec extends WordSpec with Matchers with FenceTestData {
  "ShapeFence" should {
    "not allow non-closed shapes" in {
      val shape = new PointImpl(-123.0, 47.0, JtsSpatialContext.GEO)
      assert(Try(ShapeFence(shape, Interval.all[Double])).isFailure)
    }
  }

  "Fence.contains" should {
    "identify a point within a polygon fence" in {
      assert(fence1.contains(pointInside1))
      assert(fence2.contains(pointInside2))
      assert(fence3.contains(pointInside3))
      assert(fence1.contains(Geo(pointInside1, 99)))
    }

    "identify a point outside a polygon fence" in {
      assert(!fence1.contains(pointOutside))
    }

    "identify a point outside a triangle fence" in {
      assert(!fence3.contains(pointOutside3))
    }

    "identify a point within a circle fence" in {
      assert(fenceCircle4.contains(pointInside4))
      assert(!fenceCircle4.contains(pointOutside4))
    }

    "identify a point within a height limited fence" in {
      assert(fenceMax80.contains(Geo(pointInside1, 70)))
      assert(fenceMin80.contains(Geo(pointInside1, 90)))
      assert(fenceMin60Max80.contains(Geo(pointInside1, 70)))
      assert(fenceDepth40.contains(Geo(pointInside1, -20)))
    }

    "identify a point outside a height limited fence" in {
      assert(!fenceMax80.contains(Geo(pointInside1, 90)))
      assert(!fenceMin80.contains(Geo(pointInside1, 70)))
      assert(!fenceMin60Max80.contains(Geo(pointInside1, 100)))
      assert(!fenceMin60Max80.contains(Geo(pointInside1, 40)))
      assert(!fenceDepth40.contains(Geo(pointInside1, -45)))
    }

    "identify a point within a fence crossing the dateline boundary" in {
      assert(datelineFence.contains(insideEast))
      assert(datelineFence.contains(insideWest))
    }

    "identify a point outside a fence crossing the dateline boundary" in {
      assert(!fence1.contains(pointOutside))
    }
  }

  "Fence.overlaps" should {
    "identify overlapping polygons" in {
      assert(fence2.overlaps(fence3))
      assert(fence3.overlaps(fence2))
    }

    "identify circle overlapping polygon" in {
      assert(fence1.overlaps(fenceCircle4))
      assert(fenceCircle4.overlaps(fence1))

      assert(fence2.overlaps(fenceCircle5))
      assert(fenceCircle5.overlaps(fence2))
    }

    "identify overlapping circles" in {
      assert(fenceCircle5.overlaps(fenceCircle6))
      assert(fenceCircle6.overlaps(fenceCircle5))
    }

    "identify non-overlapping polygons" in {
      assert(!fence1.overlaps(fence2))
    }

    "identify non-overlapping circles" in {
      assert(!fenceCircle5.overlaps(fenceCircle7))
    }

    "world overlaps anything" in {
      assert(Fence.world.overlaps(fence1))
      assert(Fence.world.overlaps(fence2))
      assert(Fence.world.overlaps(fence3))
      assert(Fence.world.overlaps(fenceCircle4))
      assert(Fence.world.overlaps(fenceCircle5))
      assert(Fence.world.overlaps(fenceCircle6))
      assert(Fence.world.overlaps(fenceCircle7))
    }

    "identify same polygon at different altitudes as non-overlapping" in {
      assert(!fenceMin80.overlaps(fenceMax80))
    }

    "idenfity world at different altitudes as non-overlapping" in {
      assert(!Fence.world(Interval.above(100D)).overlaps(Fence.world(Interval.below(80D))))
    }
  }

  "Fence.union" should {
    "identify a point within either of two fences" in {
      val union1 = fence1 | fence2
      assert(union1.contains(pointInside1))
      assert(union1.contains(pointInside2))

      val union2 = union1 | fenceCircle7
      assert(union2.contains(pointInside1))
      assert(union2.contains(pointInside2))
      assert(union2.contains(pointInside7))
    }

    "merge two identical fences as the same fence" in {
      fence1 | fence1 shouldBe fence1
    }

    "merge a fence with the world as the whole world" in {
      fence1 | Fence.world shouldBe Fence.world

      fenceMax80 | worldMax80 shouldBe worldMax80
      worldMax80 | fenceMax80 shouldBe worldMax80
    }

    "merge a fence with intersecting altitudes" in {
      fenceMax80 | fenceMin60Max80 shouldBe fenceMax80
    }
  }
}
