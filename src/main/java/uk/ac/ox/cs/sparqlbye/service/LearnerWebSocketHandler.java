package uk.ac.ox.cs.sparqlbye.service;

import java.util.Objects;
import java.util.Observable;
import java.util.Optional;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

/**
 * This class implements the annotations that Spark expects.
 * However, we simply delegate hooks to observer objects to the {@code LearnerController}
 * class.
 *
 * @author gdiazc
 *
 */
@WebSocket
public class LearnerWebSocketHandler extends Observable {
//	private static final Logger log = Logger.getLogger(LearnerWebSocketHandler.class.getName());

	public enum ChangeType { CONNECT, CLOSE, MESSAGE }

	public LearnerWebSocketHandler() {
		this.addObserver(LearnerController.getInstance());
	}

	@OnWebSocketConnect
	public void onConnect(Session user) throws Exception {
		setChanged();
		notifyObservers(new Update(user));
		clearChanged();
	}

	@OnWebSocketClose
	public void onClose(Session user, int statusCode, String reason) {
		setChanged();
		notifyObservers(new Update(user, statusCode, reason));
		clearChanged();
	}

	@OnWebSocketMessage
	public void onMessage(Session user, String message) {
		setChanged();
		notifyObservers(new Update(user, message));
		clearChanged();
	}

	public static class Update {
		private ChangeType type;
		private Session    user;
		private String     reason;
		private String     message;
		private Integer    statusCode;

		Update(Session user) {
			this.type = ChangeType.CONNECT;
			this.user = Objects.requireNonNull(user);
		}

		Update(Session user, int statusCode, String reason) {
			this.type       = ChangeType.CLOSE;
			this.user       = Objects.requireNonNull(user);
			this.reason     = Objects.requireNonNull(reason);
			this.statusCode = statusCode;
		}

		Update(Session user, String message) {
			this.type    = ChangeType.MESSAGE;
			this.user    = Objects.requireNonNull(user);
			this.message = Objects.requireNonNull(message);
		}

		public ChangeType getType()              { return type; }
		public Session    getUser()              { return user; }
		public Optional<Integer> getStatusCode() {
			return type.equals(ChangeType.CLOSE) ? Optional.of(statusCode) : Optional.empty();
		}
		public Optional<String> getReason() {
			return type.equals(ChangeType.CLOSE) ? Optional.of(reason) : Optional.empty();
		}
		public Optional<String> getMessage() {
			return type.equals(ChangeType.MESSAGE) ? Optional.of(message) : Optional.empty();
		}

		@Override
		public String toString() {
			return "Update(" + type + ", " + statusCode + ", " + reason + ", " + message + ")";
		}
	}

}
