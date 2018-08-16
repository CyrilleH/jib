/*
 * Copyright 2018 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import javax.annotation.Nullable;

/** Builder for Gradle-specific {@link HelpfulSuggestions}. */
class GradleHelpfulSuggestionsBuilder {

  private final String messagePrefix;
  private final JibExtension jibExtension;

  @Nullable private ImageReference baseImageReference;
  @Nullable private ImageReference targetImageReference;
  private boolean areKnownCredentialsDefinedForBaseImage;
  private boolean areKnownCredentialsDefinedForTargetImage;

  GradleHelpfulSuggestionsBuilder(String messagePrefix, JibExtension jibExtension) {
    this.messagePrefix = messagePrefix;
    this.jibExtension = jibExtension;
  }

  GradleHelpfulSuggestionsBuilder setBaseImageReference(ImageReference baseImageReference) {
    this.baseImageReference = baseImageReference;
    return this;
  }

  GradleHelpfulSuggestionsBuilder setAreKnownCredentialsDefinedForBaseImage(
      boolean areKnownCredentialsDefined) {
    areKnownCredentialsDefinedForBaseImage = areKnownCredentialsDefined;
    return this;
  }

  GradleHelpfulSuggestionsBuilder setTargetImageReference(ImageReference targetImageReference) {
    this.targetImageReference = targetImageReference;
    return this;
  }

  GradleHelpfulSuggestionsBuilder setAreKnownCredentialsDefinedForTargetImage(
      boolean areKnownCredentialsDefined) {
    areKnownCredentialsDefinedForTargetImage = areKnownCredentialsDefined;
    return this;
  }

  HelpfulSuggestions build() {
    boolean isCredHelperDefinedForBaseImage = jibExtension.getFrom().getCredHelper() != null;
    boolean isCredHelperDefinedForTargetImage = jibExtension.getTo().getCredHelper() != null;
    return new HelpfulSuggestions(
        messagePrefix,
        "gradle clean",
        baseImageReference,
        !isCredHelperDefinedForBaseImage && !areKnownCredentialsDefinedForBaseImage,
        "from.credHelper",
        ignored -> "from.auth",
        targetImageReference,
        !isCredHelperDefinedForTargetImage && !areKnownCredentialsDefinedForTargetImage,
        "to.credHelper",
        ignored -> "to.auth",
        "jib.to.image",
        "--image",
        "build.gradle");
  }
}
