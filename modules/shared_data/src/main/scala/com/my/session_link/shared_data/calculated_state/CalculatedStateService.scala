package com.my.session_link.shared_data.calculated_state

import cats.effect.Ref
import cats.effect.kernel.Async
import cats.syntax.applicative._
import cats.syntax.functor._
import com.my.session_link.shared_data.types.Types.SessionCalculatedState
import io.circe.syntax.EncoderOps
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.hash.Hash

import java.nio.charset.StandardCharsets

trait CalculatedStateService[F[_]] {
  def getCalculatedState: F[CalculatedState]

  def setCalculatedState(
    snapshotOrdinal: SnapshotOrdinal,
    state          : SessionCalculatedState
  ): F[Boolean]

  def hashCalculatedState(
    state: SessionCalculatedState
  ): F[Hash]
}

object CalculatedStateService {
  def make[F[_] : Async]: F[CalculatedStateService[F]] = {
    Ref.of[F, CalculatedState](CalculatedState.empty).map { stateRef =>
      new CalculatedStateService[F] {
        override def getCalculatedState: F[CalculatedState] = stateRef.get

        override def setCalculatedState(
          snapshotOrdinal: SnapshotOrdinal,
          state: SessionCalculatedState
        ): F[Boolean] =
          stateRef.update { currentState =>
            val currentSessionCalculatedState = currentState.state
            
           

           val thresholdOrdinal = snapshotOrdinal.value.value

           
        
           // Step 1: Update the current state with new sessions
            val mergedSessions = state.sessions.foldLeft(currentSessionCalculatedState.sessions) {
              case (acc, (sessionId, session)) =>
                acc.updated(sessionId, session)
            }

            // Step 2: Filter out expired sessions from the merged state
            val activeSessions = mergedSessions.filter {
              case (_, session) => session.endSnapshotOrdinal >= thresholdOrdinal
            }

            CalculatedState(
              snapshotOrdinal, 
              SessionCalculatedState(
                sessions = activeSessions 
              )
            )
          }.as(true)

        override def hashCalculatedState(
          state: SessionCalculatedState
        ): F[Hash] = {
          val jsonState = state.asJson.deepDropNullValues.noSpaces
          Hash.fromBytes(jsonState.getBytes(StandardCharsets.UTF_8)).pure[F]
        }
      }
    }
  }
}
