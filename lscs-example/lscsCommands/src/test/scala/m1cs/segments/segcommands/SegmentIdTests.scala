package m1cs.segments.segcommands

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class SegmentIdTests extends AnyFunSuite with Matchers {

  test("Create a good Segment Id") {
    val sector = A
    val number = 22

    val id = SegmentId(sector, number)

    id.toString shouldBe "A22"
    id.sector shouldBe sector
    id.number shouldBe number
  }

  test("Check bad value exceptions") {
    // Check for out of range segment number
    assertThrows[IllegalArgumentException] {
      SegmentId(C, 999)
    }

    assertThrows[IllegalArgumentException] {
      SegmentId(C, 0)
    }
  }

  test("Simple segment range") {
    val sector = B
    val min    = 22
    val max    = 33

    val sr1 = SegmentRange(sector, min to max)
    sr1.size shouldBe 12
    sr1.min shouldBe min
    sr1.max shouldBe max

    sr1.toString shouldBe "B[22-33]"

    //sr1.map((s:SegmentId) => println(s.number))
  }

  test("from strings for sectors, ranges, etc.") {
    Sector('a') shouldBe A
    Sector('A') shouldBe A

    assertThrows[IllegalArgumentException] {
      Sector('G')
    }

    val s1 = SegmentId("A23")
    s1.sector shouldBe A
    s1.number shouldBe 23

    val s2 = SegmentId("C1")
    s2.sector shouldBe C
    s2.number shouldBe 1

    val sr1 = SegmentRange("A[22-34]")
    sr1.sector shouldBe A
    sr1.min shouldBe 22
    sr1.max shouldBe 34
    sr1.toString shouldBe "A[22-34]"
  }

  test("test sectorId producers") {
    val sector = A
    val range  = 1 to 5
    val r1     = SegmentId.makeSectorSegmentIds(sector, range)

    r1.size shouldBe range.max
    r1 shouldBe Set(SegmentId(A, 1), SegmentId(A, 2), SegmentId(A, 3), SegmentId(A, 4), SegmentId(A, 5))

    val range2 = 1 to 10
    val r2     = SegmentId.makeSegments(range2)
    r2.size shouldBe SegmentId.ALL_SECTORS.size * range2.max

    val f = (segmentId: SegmentId) => {
      segmentId.number
    }
    import scala.concurrent.ExecutionContext.Implicits.global
    val r3 = Await.result(SegmentId.sectorMap(A, range, f), 5.seconds)
    r3.length shouldBe range.max
    r3 shouldBe List((SegmentId(A, 1), 1), (SegmentId(A, 2), 2), (SegmentId(A, 3), 3), (SegmentId(A, 4), 4), (SegmentId(A, 5), 5))
  }
}
