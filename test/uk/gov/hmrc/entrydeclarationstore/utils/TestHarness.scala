package uk.gov.hmrc.entrydeclarationstore.utils

import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.AsyncTestSuite

import scala.concurrent.ExecutionContext

trait TestHarness extends AsyncTestSuite with AsyncMockFactory {
  override implicit def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global

}
