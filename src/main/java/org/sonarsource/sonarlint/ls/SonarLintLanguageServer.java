/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.ls;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

import static java.net.URI.create;

public class SonarLintLanguageServer implements SonarLintExtendedLanguageServer, WorkspaceService, TextDocumentService {

  private static final Logger LOG = Loggers.get(SonarLintLanguageServer.class);

  private static final String TYPESCRIPT_LOCATION = "typeScriptLocation";

  private static final String SONARLINT_SOURCE = "sonarlint";
  private static final String SONARLINT_OPEN_RULE_DESCRIPTION_COMMAND = "SonarLint.OpenRuleDesc";
  private static final String SONARLINT_DEACTIVATE_RULE_COMMAND = "SonarLint.DeactivateRule";
  static final String SONARLINT_UPDATE_ALL_BINDINGS_COMMAND = "SonarLint.UpdateAllBindings";
  static final String SONARLINT_REFRESH_DIAGNOSTICS_COMMAND = "SonarLint.RefreshDiagnostics";
  private static final List<String> SONARLINT_COMMANDS = Arrays.asList(
    SONARLINT_UPDATE_ALL_BINDINGS_COMMAND,
    SONARLINT_REFRESH_DIAGNOSTICS_COMMAND);

  private final SonarLintExtendedLanguageClient client;

  private final SonarLintTelemetry telemetry = new SonarLintTelemetry();

  private final LanguageClientLogOutput clientLogOutput;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final SettingsManager settingsManager;
  private final ProjectBindingManager bindingManager;
  private final AnalysisManager analysisManager;

  /**
   * Keep track of value 'sonarlint.trace.server' on client side. Not used currently, but keeping it just in case.
   */
  private TraceValues traceLevel;

  private final ExecutorService threadPool;

  SonarLintLanguageServer(InputStream inputStream, OutputStream outputStream, Collection<URL> analyzers) {
    threadPool = Executors.newCachedThreadPool(Utils.threadFactory("SonarLint LSP message processor", false));
    Launcher<SonarLintExtendedLanguageClient> launcher = new Launcher.Builder<SonarLintExtendedLanguageClient>()
      .setLocalService(this)
      .setRemoteInterface(SonarLintExtendedLanguageClient.class)
      .setInput(inputStream)
      .setOutput(outputStream)
      .setExecutorService(threadPool)
      .create();

    this.client = launcher.getRemoteProxy();

    clientLogOutput = new LanguageClientLogOutput(this.client);
    Loggers.setTarget(clientLogOutput);
    this.settingsManager = new SettingsManager(this.client);
    this.settingsManager.addListener(telemetry);
    this.workspaceFoldersManager = new WorkspaceFoldersManager(settingsManager);
    this.bindingManager = new ProjectBindingManager(workspaceFoldersManager, settingsManager, clientLogOutput, client);
    this.settingsManager.addListener((WorkspaceSettingsChangeListener) bindingManager);
    this.settingsManager.addListener((WorkspaceFolderSettingsChangeListener) bindingManager);
    this.workspaceFoldersManager.addListener(settingsManager);
    this.analysisManager = new AnalysisManager(analyzers, clientLogOutput, client, telemetry, workspaceFoldersManager, settingsManager, bindingManager);
    launcher.startListening();
  }

  static SonarLintLanguageServer bySocket(int port, Collection<URL> analyzers) throws IOException {
    Socket socket = new Socket("localhost", port);
    return new SonarLintLanguageServer(socket.getInputStream(), socket.getOutputStream(), analyzers);
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      this.traceLevel = parseTraceLevel(params.getTrace());

      workspaceFoldersManager.initialize(params.getWorkspaceFolders());

      Map<String, Object> options = Utils.parseToMap(params.getInitializationOptions());

      String productKey = (String) options.get("productKey");
      // deprecated, will be ignored when productKey present
      String telemetryStorage = (String) options.get("telemetryStorage");

      String productName = (String) options.get("productName");
      String productVersion = (String) options.get("productVersion");
      String ideVersion = (String) options.get("ideVersion");

      Optional<String> typeScriptPath = Optional.ofNullable((String) options.get(TYPESCRIPT_LOCATION));

      bindingManager.initialize(typeScriptPath.map(Paths::get).orElse(null));
      analysisManager.initialize(typeScriptPath.map(Paths::get).orElse(null));

      telemetry.init(productKey, telemetryStorage, productName, productVersion, ideVersion, bindingManager::usesConnectedMode, bindingManager::usesSonarCloud);

      InitializeResult result = new InitializeResult();
      ServerCapabilities c = new ServerCapabilities();
      c.setTextDocumentSync(getTextDocumentSyncOptions());
      c.setCodeActionProvider(true);
      c.setExecuteCommandProvider(new ExecuteCommandOptions(SONARLINT_COMMANDS));
      c.setWorkspace(getWorkspaceServerCapabilities());

      result.setCapabilities(c);
      return result;
    });
  }

  private static WorkspaceServerCapabilities getWorkspaceServerCapabilities() {
    WorkspaceFoldersOptions options = new WorkspaceFoldersOptions();
    options.setSupported(true);
    options.setChangeNotifications(true);

    WorkspaceServerCapabilities capabilities = new WorkspaceServerCapabilities();
    capabilities.setWorkspaceFolders(options);
    return capabilities;
  }

  private static TextDocumentSyncOptions getTextDocumentSyncOptions() {
    TextDocumentSyncOptions textDocumentSyncOptions = new TextDocumentSyncOptions();
    textDocumentSyncOptions.setOpenClose(true);
    textDocumentSyncOptions.setChange(TextDocumentSyncKind.Full);
    textDocumentSyncOptions.setSave(new SaveOptions(true));
    return textDocumentSyncOptions;
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      analysisManager.shutdown();
      bindingManager.shutdown();
      telemetry.stop();
      settingsManager.shutdown();
      threadPool.shutdown();
      return new Object();
    });
  }

  @Override
  public void exit() {
    // The Socket will be closed by the client, and so remaining threads will die and the JVM will terminate
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return this;
  }

  @Override
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      List<Either<Command, CodeAction>> commands = new ArrayList<>();
      URI uri = create(params.getTextDocument().getUri());
      Optional<ProjectBindingWrapper> binding = bindingManager.getBinding(uri);
      for (Diagnostic d : params.getContext().getDiagnostics()) {
        if (SONARLINT_SOURCE.equals(d.getSource())) {
          String ruleKey = d.getCode();
          List<Object> ruleDescriptionParams = getOpenRuleDescriptionParams(binding, ruleKey);
          // May take time to initialize the engine so check for cancellation just after
          cancelToken.checkCanceled();
          if (!ruleDescriptionParams.isEmpty()) {
            commands.add(Either.forLeft(
              new Command(String.format("Open description of SonarLint rule '%s'", ruleKey),
                SONARLINT_OPEN_RULE_DESCRIPTION_COMMAND,
                ruleDescriptionParams)));
          }
          if (!binding.isPresent()) {
            commands.add(Either.forLeft(
              new Command(String.format("Deactivate rule '%s'", ruleKey),
                SONARLINT_DEACTIVATE_RULE_COMMAND,
                Collections.singletonList(ruleKey))));
          }
        }
      }
      return commands;
    });
  }

  private List<Object> getOpenRuleDescriptionParams(Optional<ProjectBindingWrapper> binding, String ruleKey) {
    RuleDetails ruleDetails;
    if (!binding.isPresent()) {
      ruleDetails = analysisManager.getOrCreateStandaloneEngine().getRuleDetails(ruleKey)
        .orElseThrow(() -> new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Unknown rule with key: " + ruleKey, null)));
    } else {
      ConnectedSonarLintEngine engine = binding.get().getEngine();
      if (engine != null) {
        ruleDetails = engine.getRuleDetails(ruleKey);
      } else {
        return Collections.emptyList();
      }
    }
    String ruleName = ruleDetails.getName();
    String htmlDescription = getHtmlDescription(ruleDetails);
    String type = ruleDetails.getType();
    String severity = ruleDetails.getSeverity();
    return Arrays.asList(ruleKey, ruleName, htmlDescription, type, severity);
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    URI uri = create(params.getTextDocument().getUri());
    analysisManager.didOpen(uri, params.getTextDocument().getLanguageId(), params.getTextDocument().getText());
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    URI uri = create(params.getTextDocument().getUri());
    analysisManager.didChange(uri, params.getContentChanges().get(0).getText());
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    URI uri = create(params.getTextDocument().getUri());
    analysisManager.didClose(uri);
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    URI uri = create(params.getTextDocument().getUri());
    analysisManager.didSave(uri, params.getText());
  }

  @Override
  public CompletableFuture<Map<String, List<RuleDescription>>> listAllRules() {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      Map<String, List<RuleDescription>> result = new HashMap<>();
      Map<String, String> languagesNameByKey = analysisManager.getOrCreateStandaloneEngine().getAllLanguagesNameByKey();
      analysisManager.getOrCreateStandaloneEngine().getAllRuleDetails()
        .forEach(d -> {
          String languageName = languagesNameByKey.get(d.getLanguageKey());
          if (!result.containsKey(languageName)) {
            result.put(languageName, new ArrayList<>());
          }
          result.get(languageName).add(RuleDescription.of(d));
        });
      return result;
    });
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return this;
  }

  @Override
  public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      List<Object> args = params.getArguments();
      switch (params.getCommand()) {
        case SONARLINT_UPDATE_ALL_BINDINGS_COMMAND:
          bindingManager.updateAllBindings();
          break;
        case SONARLINT_REFRESH_DIAGNOSTICS_COMMAND:
          Gson gson = new Gson();
          List<Document> docsToRefresh = args == null ? Collections.emptyList()
            : args.stream().map(arg -> gson.fromJson(arg.toString(), Document.class)).collect(Collectors.toList());
          docsToRefresh.forEach(doc -> analysisManager.analyzeAsync(create(doc.uri), false));
          break;
        default:
          throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Unsupported command: " + params.getCommand(), null));
      }
      return null;
    });
  }

  // visible for testing
  static String getHtmlDescription(RuleDetails ruleDetails) {
    String htmlDescription = ruleDetails.getHtmlDescription();
    String extendedDescription = ruleDetails.getExtendedDescription();
    if (!extendedDescription.isEmpty()) {
      htmlDescription += "<div>" + extendedDescription + "</div>";
    }
    return htmlDescription;
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {
    settingsManager.didChangeConfiguration();
    workspaceFoldersManager.didChangeConfiguration();
  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    // No watched files
  }

  @Override
  public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
    workspaceFoldersManager.didChangeWorkspaceFolders(params.getEvent());
  }

  @Override
  public void setTraceNotification(SetTraceNotificationParams params) {
    this.traceLevel = parseTraceLevel(params.getValue());
  }

  private static TraceValues parseTraceLevel(@Nullable String trace) {
    return Optional.ofNullable(trace)
      .map(String::toUpperCase)
      .map(TraceValues::valueOf)
      .orElse(TraceValues.OFF);
  }

  public static class Document {
    final String uri;

    public Document(String uri) {
      this.uri = uri;
    }
  }
}
