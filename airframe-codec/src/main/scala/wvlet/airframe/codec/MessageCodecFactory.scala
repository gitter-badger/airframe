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
package wvlet.airframe.codec

import wvlet.airframe.codec.CollectionCodec._
import wvlet.airframe.codec.ScalaStandardCodec.{OptionCodec, TupleCodec}
import wvlet.airframe.codec.StandardCodec.EnumCodec
import wvlet.log.LogSupport
import wvlet.surface
import wvlet.surface.reflect.{ReflectTypeUtil, SurfaceFactory}
import wvlet.surface.{EnumSurface, GenericSurface, Surface}

import scala.reflect.runtime.{universe => ru}

/**
  *
  */
class MessageCodecFactory(knownCodecs: Map[Surface, MessageCodec[_]]) extends LogSupport {

  def withCodecs(additionalCodecs: Map[Surface, MessageCodec[_]]): MessageCodecFactory = {
    new MessageCodecFactory(knownCodecs ++ additionalCodecs)
  }

  private var cache = Map.empty[Surface, MessageCodec[_]]

  private def ofSurface(surface: Surface, seen: Set[Surface] = Set.empty): MessageCodec[_] = {
    // TODO Create a fast object codec with code generation (e.g., Scala macros)

    if (knownCodecs.contains(surface)) {
      knownCodecs(surface)
    } else if (cache.contains(surface)) {
      cache(surface)
    } else if (seen.contains(surface)) {
      throw new IllegalArgumentException(s"Codec for recursive types is not supported: ${surface}")
    } else {
      val seenSet = seen + surface
      trace(s"Finding MessageCodec of ${surface.dealias}")
      val codec =
        surface.dealias match {
          case s if s.isOption =>
            // Option type
            val elementSurface = surface.typeArgs(0)
            OptionCodec(ofSurface(elementSurface, seenSet))
          case s if ReflectTypeUtil.isTuple(s.rawType) =>
            TupleCodec(s.typeArgs.map(x => ofSurface(x)))
          case EnumSurface(cl) =>
            EnumCodec(cl)
          case g: GenericSurface if ReflectTypeUtil.isSeq(g.rawType) =>
            // Seq[A]
            if (ReflectTypeUtil.isIndexedSeq(g.rawType)) {
              IndexedSeqCodec(g.typeArgs(0), ofSurface(g.typeArgs(0), seenSet))
            } else {
              SeqCodec(g.typeArgs(0), ofSurface(g.typeArgs(0), seenSet))
            }
          case g: GenericSurface if ReflectTypeUtil.isJavaColleciton(g.rawType) =>
            JavaListCodec(ofSurface(g.typeArgs(0), seenSet))
          case g: GenericSurface if ReflectTypeUtil.isMap(g.rawType) =>
            // Map[A,B]
            MapCodec(ofSurface(g.typeArgs(0), seen), ofSurface(g.typeArgs(1), seenSet))
          case g: GenericSurface if ReflectTypeUtil.isJavaMap(g.rawType) =>
            // Map[A,B]
            JavaMapCodec(ofSurface(g.typeArgs(0), seen), ofSurface(g.typeArgs(1), seenSet))
          case s if ReflectTypeUtil.isTuple(s.rawType) =>
            // Tuple
            TupleCodec(surface.typeArgs.map(x => ofSurface(x, seenSet)))
          case _ =>
            val codecs = for (p <- surface.params) yield {
              ofSurface(p.surface, seenSet)
            }
            ObjectCodec(surface, codecs.toIndexedSeq)
        }
      cache += surface -> codec
      codec
    }
  }

  def of[A: ru.TypeTag]: MessageCodec[A]    = ofSurface(surface.of[A]).asInstanceOf[MessageCodec[A]]
  def of(surface: Surface): MessageCodec[_] = ofSurface(surface)
  def ofType(tpe: ru.Type): MessageCodec[_] = ofSurface(SurfaceFactory.ofType(tpe))

}
