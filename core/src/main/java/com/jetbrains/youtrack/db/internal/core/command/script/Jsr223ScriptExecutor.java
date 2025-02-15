package com.jetbrains.youtrack.db.internal.core.command.script;

import com.jetbrains.youtrack.db.api.exception.CommandScriptException;
import com.jetbrains.youtrack.db.api.exception.BaseException;
import com.jetbrains.youtrack.db.internal.common.util.CommonConst;
import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.command.script.transformer.ScriptTransformer;
import com.jetbrains.youtrack.db.internal.core.command.traverse.AbstractScriptExecutor;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseSessionInternal;
import com.jetbrains.youtrack.db.api.exception.CommandExecutionException;
import com.jetbrains.youtrack.db.internal.core.metadata.function.Function;
import com.jetbrains.youtrack.db.internal.core.metadata.security.Role;
import com.jetbrains.youtrack.db.internal.core.metadata.security.Rule;
import com.jetbrains.youtrack.db.api.query.ResultSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 *
 */
public class Jsr223ScriptExecutor extends AbstractScriptExecutor {

  private final ScriptTransformer transformer;

  public Jsr223ScriptExecutor(String language, ScriptTransformer scriptTransformer) {
    super(language);
    this.language = language;
    this.transformer = scriptTransformer;
  }

  @Override
  public ResultSet execute(DatabaseSessionInternal database, String script, Object... params) {

    preExecute(database, script, params);

    Int2ObjectOpenHashMap<Object> par = new Int2ObjectOpenHashMap<>();

    for (int i = 0; i < params.length; i++) {
      par.put(i, params[i]);
    }
    return execute(database, script, par);
  }

  @Override
  public ResultSet execute(DatabaseSessionInternal database, String script, Map params) {

    preExecute(database, script, params);

    final ScriptManager scriptManager =
        database.getSharedContext().getYouTrackDB().getScriptManager();
    CompiledScript compiledScript = null;

    final ScriptEngine scriptEngine =
        scriptManager.acquireDatabaseEngine(database.getName(), language);
    try {

      if (!(scriptEngine instanceof Compilable c)) {
        throw new CommandExecutionException(
            "Language '" + language + "' does not support compilation");
      }

      try {
        compiledScript = c.compile(script);
      } catch (ScriptException e) {
        scriptManager.throwErrorMessage(e, script);
      }

      final Bindings binding =
          scriptManager.bindContextVariables(
              compiledScript.getEngine(),
              compiledScript.getEngine().getBindings(ScriptContext.ENGINE_SCOPE),
              database,
              null,
              params);

      try {
        final Object ob = compiledScript.eval(binding);
        return transformer.toResultSet(database, ob);
      } catch (ScriptException e) {
        throw BaseException.wrapException(
            new CommandScriptException(
                "Error on execution of the script", script, e.getColumnNumber()),
            e);

      } finally {
        scriptManager.unbind(scriptEngine, binding, null, params);
      }
    } finally {
      scriptManager.releaseDatabaseEngine(language, database.getName(), scriptEngine);
    }
  }

  @Override
  public Object executeFunction(
      CommandContext context, final String functionName, final Map<Object, Object> iArgs) {

    DatabaseSessionInternal db = context.getDatabase();
    final Function f = db.getMetadata().getFunctionLibrary().getFunction(functionName);

    db.checkSecurity(Rule.ResourceGeneric.FUNCTION, Role.PERMISSION_READ, f.getName(db));

    final ScriptManager scriptManager = db.getSharedContext().getYouTrackDB().getScriptManager();

    final ScriptEngine scriptEngine =
        scriptManager.acquireDatabaseEngine(db.getName(), f.getLanguage(db));
    try {
      final Bindings binding =
          scriptManager.bind(
              scriptEngine,
              scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE),
              db,
              context,
              iArgs);

      try {
        final Object result;

        if (scriptEngine instanceof Invocable invocableEngine) {
          // INVOKE AS FUNCTION. PARAMS ARE PASSED BY POSITION
          Object[] args = null;
          if (iArgs != null) {
            args = new Object[iArgs.size()];
            int i = 0;
            for (Entry<Object, Object> arg : iArgs.entrySet()) {
              args[i++] = arg.getValue();
            }
          } else {
            args = CommonConst.EMPTY_OBJECT_ARRAY;
          }
          result = invocableEngine.invokeFunction(functionName, args);

        } else {
          // INVOKE THE CODE SNIPPET
          final Object[] args = iArgs == null ? null : iArgs.values().toArray();
          result = scriptEngine.eval(scriptManager.getFunctionInvoke(db, f, args), binding);
        }
        return CommandExecutorUtility.transformResult(
            scriptManager.handleResult(f.getLanguage(db), result, scriptEngine, binding, db));

      } catch (ScriptException e) {
        throw BaseException.wrapException(
            new CommandScriptException(
                "Error on execution of the script", functionName, e.getColumnNumber()),
            e);
      } catch (NoSuchMethodException e) {
        throw BaseException.wrapException(
            new CommandScriptException("Error on execution of the script", functionName, 0), e);
      } catch (CommandScriptException e) {
        // PASS THROUGH
        throw e;

      } finally {
        scriptManager.unbind(scriptEngine, binding, context, iArgs);
      }
    } finally {
      scriptManager.releaseDatabaseEngine(f.getLanguage(db), db.getName(), scriptEngine);
    }
  }
}
