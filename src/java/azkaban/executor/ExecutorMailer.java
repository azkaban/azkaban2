/*
 * Copyright 2012 LinkedIn Corp.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.executor;

import java.util.ArrayList;
import java.util.List;

import javax.mail.MessagingException;

import org.apache.log4j.Logger;

import azkaban.executor.mail.DefaultMailCreator;
import azkaban.executor.mail.MailCreator;
import azkaban.utils.EmailMessage;
import azkaban.utils.Props;
import azkaban.utils.Utils;

public class ExecutorMailer {
	private static Logger logger = Logger.getLogger(ExecutorMailer.class);

	private boolean testMode = false;

	private String clientHostname;
	private String clientPortNumber;

	private String mailHost;
	private String mailUser;
	private String mailPassword;
	private String mailSender;
	private String azkabanName;

	public ExecutorMailer(Props props) {
		this.azkabanName = props.getString("azkaban.name", "azkaban");
		this.mailHost = props.getString("mail.host", "localhost");
		this.mailUser = props.getString("mail.user", "");
		this.mailPassword = props.getString("mail.password", "");
		this.mailSender = props.getString("mail.sender", "");

		int mailTimeout = props.getInt("mail.timeout.millis", 10000);
		EmailMessage.setTimeout(mailTimeout);
		int connectionTimeout = props.getInt("mail.connection.timeout.millis", 10000);
		EmailMessage.setConnectionTimeout(connectionTimeout);
		
		this.clientHostname = props.getString("jetty.hostname", "localhost");
		
		if (props.getBoolean("jetty.use.ssl", true)) {
			this.clientPortNumber = props.getString("jetty.ssl.port");
		} else {
			this.clientPortNumber = props.getString("jetty.port");
		}
		
		testMode = props.getBoolean("test.mode", false);
	}

	public void sendFirstErrorMessage(ExecutableFlow flow) {
		EmailMessage message = new EmailMessage(mailHost, mailUser, mailPassword);
		message.setFromAddress(mailSender);

		ExecutionOptions option = flow.getExecutionOptions();

		MailCreator mailCreator = DefaultMailCreator.getCreator(option.getMailCreator());

		logger.debug("ExecutorMailer using mail creator:" + mailCreator.getClass().getCanonicalName());

		boolean mailCreated = mailCreator.createFirstErrorMessage(flow, message, azkabanName, clientHostname, clientPortNumber);

		if (mailCreated && !testMode) {
			try {
				message.sendEmail();
			} catch (MessagingException e) {
				logger.error("Email message send failed", e);
			}
		}
	}

	public void sendErrorEmail(ExecutableFlow flow, String... extraReasons) {
		EmailMessage message = new EmailMessage(mailHost, mailUser, mailPassword);
		message.setFromAddress(mailSender);

		ExecutionOptions option = flow.getExecutionOptions();

		MailCreator mailCreator = DefaultMailCreator.getCreator(option.getMailCreator());
		logger.debug("ExecutorMailer using mail creator:" + mailCreator.getClass().getCanonicalName());

		boolean mailCreated = mailCreator.createErrorEmail(flow, message, azkabanName, clientHostname, clientPortNumber, extraReasons);

		if (mailCreated && !testMode) {
			try {
				message.sendEmail();
			} catch (MessagingException e) {
				logger.error("Email message send failed", e);
			}
		}
	}

	public void sendSuccessEmail(ExecutableFlow flow) {
		EmailMessage message = new EmailMessage(mailHost, mailUser, mailPassword);
		message.setFromAddress(mailSender);

		ExecutionOptions option = flow.getExecutionOptions();

		MailCreator mailCreator = DefaultMailCreator.getCreator(option.getMailCreator());
		logger.debug("ExecutorMailer using mail creator:" + mailCreator.getClass().getCanonicalName());

		boolean mailCreated = mailCreator.createSuccessEmail(flow, message, azkabanName, clientHostname, clientPortNumber);

		if (mailCreated && !testMode) {
			try {
				message.sendEmail();
			} catch (MessagingException e) {
				logger.error("Email message send failed", e);
			}
		}
	}

	public static List<String> findFailedJobs(ExecutableFlow flow) {
		ArrayList<String> failedJobs = new ArrayList<String>();
		for (ExecutableNode node : flow.getExecutableNodes()) {
			if (node.getStatus() == Status.FAILED) {
				failedJobs.add(node.getJobId());
			}
		}

		return failedJobs;
	}
}
