package com.orientechnologies.core.sql.executor.resultset;

import com.orientechnologies.core.command.OCommandContext;
import com.orientechnologies.core.sql.executor.YTResult;

public interface OMapExecutionStream {

  OExecutionStream flatMap(YTResult next, OCommandContext ctx);
}
