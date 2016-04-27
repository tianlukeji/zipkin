/**
 * Copyright 2015-2016 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin;

import java.util.Arrays;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.WEB_ENDPOINT;

public class SpanTest {

  @Test
  public void spanNamesLowercase() {
    assertThat(new Span.Builder().traceId(1L).id(1L).name("GET").build().name)
        .isEqualTo("get");
  }

  @Test
  public void mergeWhenBinaryAnnotationsSentSeparately() {
    Span part1 = new Span.Builder()
        .traceId(1L)
        .name("")
        .id(1L)
        .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, WEB_ENDPOINT))
        .build();

    Span part2 = new Span.Builder()
        .traceId(1L)
        .name("get")
        .id(1L)
        .timestamp(1444438900939000L)
        .duration(376000L)
        .addAnnotation(Annotation.create(1444438900939000L, Constants.SERVER_RECV, WEB_ENDPOINT))
        .addAnnotation(Annotation.create(1444438901315000L, Constants.SERVER_SEND, WEB_ENDPOINT))
        .build();

    Span expected = new Span.Builder(part2)
        .addBinaryAnnotation(part1.binaryAnnotations.get(0))
        .build();

    assertThat(new Span.Builder(part1).merge(part2).build()).isEqualTo(expected);
    assertThat(new Span.Builder(part2).merge(part1).build()).isEqualTo(expected);
  }

  /**
   * Some instrumentation set name to "unknown" or empty. This ensures dummy span names lose on
   * merge.
   */
  @Test
  public void mergeOverridesDummySpanNames() {
    for (String nonName : Arrays.asList("", "unknown")) {
      Span unknown = new Span.Builder().traceId(1).id(2).name(nonName).build();
      Span get = new Span.Builder(unknown).name("get").build();

      assertThat(new Span.Builder(unknown).merge(get).build().name).isEqualTo("get");
      assertThat(new Span.Builder(get).merge(unknown).build().name).isEqualTo("get");
    }
  }

  @Test
  public void serviceNames_includeBinaryAnnotations() {
    Span span = new Span.Builder()
        .traceId(1L)
        .name("GET")
        .id(1L)
        .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, WEB_ENDPOINT))
        .build();

    assertThat(span.serviceNames())
        .containsOnly(WEB_ENDPOINT.serviceName);
  }

  @Test
  public void serviceNames_ignoresAnnotationsWithEmptyServiceNames() {
    Span span = new Span.Builder()
        .traceId(12345)
        .id(666)
        .name("methodcall")
        .addAnnotation(Annotation.create(1L, "test", Endpoint.create("", 127 << 24 | 1)))
        .addAnnotation(Annotation.create(2L, Constants.SERVER_RECV, WEB_ENDPOINT))
        .build();

    assertThat(span.serviceNames())
        .containsOnly(WEB_ENDPOINT.serviceName);
  }

  /** This helps tests not flake out when binary annotations aren't returned in insertion order */
  @Test
  public void sortsBinaryAnnotationsByKey() {
    BinaryAnnotation foo = BinaryAnnotation.create("foo", "bar", WEB_ENDPOINT);
    BinaryAnnotation baz = BinaryAnnotation.create("baz", "qux", WEB_ENDPOINT);
    Span span = new Span.Builder()
        .traceId(12345)
        .id(666)
        .name("methodcall")
        .addBinaryAnnotation(foo)
        .addBinaryAnnotation(baz)
        .build();

    assertThat(span.binaryAnnotations)
        .containsExactly(baz, foo);
  }
}
