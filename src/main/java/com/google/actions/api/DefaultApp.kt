/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.actions.api

import com.google.actions.api.impl.AogResponse
import com.google.actions.api.impl.DialogflowResponse
import com.google.actions.api.impl.io.ResponseSerializer
import com.google.actions.api.response.ResponseBuilder
import java.util.concurrent.CompletableFuture

/**
 * Default implementation of an Actions App. This class provides most of the
 * functionality of an App such as request parsing and routing.
 */
abstract class DefaultApp : App {

  /**
   * Creates an ActionRequest for the specified JSON and metadata.
   * @param inputJson The input JSON.
   * @param headers Map containing metadata, usually from the HTTP request
   *   headers.
   */
  abstract fun createRequest(inputJson: String, headers: Map<*, *>?):
          ActionRequest

  /**
   * @return A ResponseBuilder for this App.
   */
  abstract fun getResponseBuilder(): ResponseBuilder

  override fun handleRequest(
          inputJson: String?, headers: Map<*, *>?): CompletableFuture<String> {
    if (inputJson == null || inputJson.isEmpty()) {
      return handleError("Invalid or empty JSON")
    }

    val request: ActionRequest
    val future: CompletableFuture<ActionResponse>
    try {
      request = createRequest(inputJson, headers)
      future = routeRequest(request)
    } catch (e: Exception) {
      return handleError(e)
    }

    return future
            .thenApply { this.getAsJson(it, request) }
            .exceptionally { throwable -> throwable.message }
  }

  @Throws(Exception::class)
  fun routeRequest(request: ActionRequest): CompletableFuture<ActionResponse> {
    val intent = request.intent
    val forIntentType = ForIntent::class.java
    for (method in javaClass.declaredMethods) {
      if (method.isAnnotationPresent(forIntentType)) {
        val annotation = method.getAnnotation(forIntentType)
        val forIntent = annotation as ForIntent
        if (forIntent.value == intent) {
          return method.invoke(this, request) as
                  CompletableFuture<ActionResponse>
        }
      }
    }
    // Unable to find a method with the annotation matching the intent.
    throw Exception("Intent handler not found - $intent")
  }

  fun handleError(exception: Exception): CompletableFuture<String> {
    exception.printStackTrace()
    return handleError(exception.message)
  }

  private fun handleError(message: String?): CompletableFuture<String> {
    val future = CompletableFuture<String>()
    future.completeExceptionally(Exception(message))
    return future
  }

  private fun getAsJson(
          response: ActionResponse,
          request: ActionRequest): String {
    val responseSerializer = ResponseSerializer(request.sessionId)
    when (response) {
      is DialogflowResponse ->
        response.conversationData = request.conversationData
      is AogResponse -> response.conversationData = request.conversationData
    }
    return responseSerializer.toJsonV2(response)
  }
}