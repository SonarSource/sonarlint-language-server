package org.sonarsource.sonarlint.ls.watcher;

public class IssueParams {

	private String message;

	public IssueParams(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

}
