/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wvlet.airframe

import javax.annotation.{PostConstruct, PreDestroy}

trait JSR250Test {
  var initialized = false
  var stopped     = false

  @PostConstruct
  def init {
    initialized = true
  }

  @PreDestroy
  def stop {
    stopped = true
  }
}

/**
  *
  */
class JSR250LifeCycleExecutorTest extends AirframeSpec {
  "Airframe" should {
    "support JSR250 event" in {
      val s = blancSession
      val t = s.build[JSR250Test]
      t.initialized shouldBe true
      t.stopped shouldBe false
      s.start {}
      t.initialized shouldBe true
      t.stopped shouldBe true
    }
  }
}
