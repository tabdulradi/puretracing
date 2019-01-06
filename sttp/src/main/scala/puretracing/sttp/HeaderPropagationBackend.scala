package puretracing.sttp

import com.softwaremill.sttp.{MonadError, Request, Response, SttpBackend}
import puretracing.api.Propagation

class HeaderPropagationBackend[R[_], S](delegate: SttpBackend[R, S])(implicit propagation: Propagation[R]) extends SttpBackend[R, S] {

  override def send[T](request: Request[T, S]): R[Response[T]] =
    responseMonad.flatMap(propagation.currentSpan){ span =>
      responseMonad.flatMap(propagation.export(span)){ tracingHeaders =>
        delegate.send(request.copy(headers = request.headers ++ tracingHeaders))
      }
    }

  override def close(): Unit = delegate.close()
  override def responseMonad: MonadError[R] = delegate.responseMonad
}
