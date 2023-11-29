/*
 * SonarLint Language Server
 * Copyright (C) 2009-2023 SonarSource SA
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

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttributeCategory;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleParamDto;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowRuleDescriptionParams;

import static org.assertj.core.api.Assertions.assertThat;

class SonarLintExtendedLanguageClientTests {

  @Test
  void test_rule_parameter_equals_hashCode() {
    var param1 = new EffectiveRuleParamDto("name", "description", "42", "50");
    var ruleDescTabs = new SonarLintExtendedLanguageClient.RuleDescriptionTab[]{new SonarLintExtendedLanguageClient.RuleDescriptionTab("ruleDescTitle", new SonarLintExtendedLanguageClient.RuleDescriptionTabNonContextual("ruleDesc"))};
    var ruleDesc1 = new ShowRuleDescriptionParams("key1", "name1", "desc1", ruleDescTabs, RuleType.BUG, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.singleton(param1), CleanCodeAttribute.TRUSTWORTHY.getLabel(), CleanCodeAttributeCategory.RESPONSIBLE.getLabel(), Map.of(SoftwareQuality.SECURITY.getLabel(), ImpactSeverity.HIGH.getLabel()));
    var ruleDescSame1 = new ShowRuleDescriptionParams("key1", "name1", "desc1", ruleDescTabs, RuleType.BUG, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.singleton(param1), CleanCodeAttribute.TRUSTWORTHY.getLabel(), CleanCodeAttributeCategory.RESPONSIBLE.getLabel(), Map.of(SoftwareQuality.SECURITY.getLabel(), ImpactSeverity.HIGH.getLabel()));

    assertThat(ruleDesc1).hasSameHashCodeAs(ruleDescSame1)
      .isEqualTo(ruleDesc1)
      .isEqualTo(ruleDescSame1)
      .isNotEqualTo("foo")
      .isNotEqualTo(null);

    var ruleDescDiffKey = new ShowRuleDescriptionParams("key2", "name1", "desc1", ruleDescTabs, RuleType.BUG, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.singleton(param1), CleanCodeAttribute.TRUSTWORTHY.getLabel(), CleanCodeAttributeCategory.RESPONSIBLE.getLabel(), Map.of(SoftwareQuality.SECURITY.getLabel(), ImpactSeverity.HIGH.getLabel()));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffKey.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffKey);

    var ruleDescDiffName = new ShowRuleDescriptionParams("key1", "name2", "desc1", ruleDescTabs, RuleType.BUG, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.singleton(param1), CleanCodeAttribute.TRUSTWORTHY.getLabel(), CleanCodeAttributeCategory.RESPONSIBLE.getLabel(), Map.of(SoftwareQuality.SECURITY.getLabel(), ImpactSeverity.HIGH.getLabel()));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffName.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffName);

    var ruleDescDiffDesc = new ShowRuleDescriptionParams("key1", "name1", "desc2", ruleDescTabs, RuleType.BUG, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.singleton(param1), CleanCodeAttribute.TRUSTWORTHY.getLabel(), CleanCodeAttributeCategory.RESPONSIBLE.getLabel(), Map.of(SoftwareQuality.SECURITY.getLabel(), ImpactSeverity.HIGH.getLabel()));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffDesc.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffDesc);

    var ruleDescDiffType = new ShowRuleDescriptionParams("key1", "name1", "desc1", ruleDescTabs, RuleType.VULNERABILITY, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.singleton(param1), CleanCodeAttribute.TRUSTWORTHY.getLabel(), CleanCodeAttributeCategory.RESPONSIBLE.getLabel(), Map.of(SoftwareQuality.SECURITY.getLabel(), ImpactSeverity.HIGH.getLabel()));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffType.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffType);

    var ruleDescDiffSeverity = new ShowRuleDescriptionParams("key1", "name1", "desc1", ruleDescTabs, RuleType.BUG, Language.JAVA.getLanguageKey(), IssueSeverity.CRITICAL, Collections.singleton(param1), CleanCodeAttribute.TRUSTWORTHY.getLabel(), CleanCodeAttributeCategory.RESPONSIBLE.getLabel(), Map.of(SoftwareQuality.SECURITY.getLabel(), ImpactSeverity.HIGH.getLabel()));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffSeverity.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffSeverity);

    var ruleDescDiffParams = new ShowRuleDescriptionParams("key1", "name1", "desc1", ruleDescTabs, RuleType.BUG, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.emptyList(), CleanCodeAttribute.TRUSTWORTHY.getLabel(), CleanCodeAttributeCategory.RESPONSIBLE.getLabel(), Map.of(SoftwareQuality.SECURITY.getLabel(), ImpactSeverity.HIGH.getLabel()));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffParams.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffParams);

    var ruleDescDiffCleanCodeAttr = new ShowRuleDescriptionParams("key1", "name1", "desc1", ruleDescTabs, RuleType.BUG, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.singleton(param1), CleanCodeAttribute.COMPLETE.getLabel(), CleanCodeAttributeCategory.RESPONSIBLE.getLabel(), Map.of(SoftwareQuality.SECURITY.getLabel(), ImpactSeverity.HIGH.getLabel()));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffCleanCodeAttr.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffCleanCodeAttr);

    var ruleDescDiffCleanCodeCategory = new ShowRuleDescriptionParams("key1", "name1", "desc1", ruleDescTabs, RuleType.BUG, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.singleton(param1), CleanCodeAttribute.TRUSTWORTHY.getLabel(), CleanCodeAttributeCategory.INTENTIONAL.getLabel(), Map.of(SoftwareQuality.SECURITY.getLabel(), ImpactSeverity.HIGH.getLabel()));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffCleanCodeCategory.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffCleanCodeCategory);

    var ruleDescDiffImpacts = new ShowRuleDescriptionParams("key1", "name1", "desc1", ruleDescTabs, RuleType.BUG, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.singleton(param1), CleanCodeAttribute.TRUSTWORTHY.getLabel(), CleanCodeAttributeCategory.INTENTIONAL.getLabel(), Map.of(SoftwareQuality.SECURITY.getLabel(), ImpactSeverity.LOW.getLabel()));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffImpacts.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffImpacts);

    var ruleDescTabs2 = new SonarLintExtendedLanguageClient.RuleDescriptionTab[]{
      new SonarLintExtendedLanguageClient.RuleDescriptionTab("ruleDescTitle", new SonarLintExtendedLanguageClient.RuleDescriptionTabNonContextual("ruleDesc")),
      new SonarLintExtendedLanguageClient.RuleDescriptionTab("ruleDescTitle1", new SonarLintExtendedLanguageClient.RuleDescriptionTabNonContextual("ruleDesc1"))
    };
    var ruleDescDiffDescTabs = new ShowRuleDescriptionParams("key1", "name1", "desc1", ruleDescTabs2, RuleType.BUG, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.singleton(param1), CleanCodeAttribute.TRUSTWORTHY.getLabel(), CleanCodeAttributeCategory.RESPONSIBLE.getLabel(), Map.of(SoftwareQuality.SECURITY.getLabel(), ImpactSeverity.HIGH.getLabel()));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffDescTabs.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffDescTabs);

    var exposedParam = ruleDesc1.getParameters()[0];
    assertThat(exposedParam).hasSameHashCodeAs(ruleDescSame1.getParameters()[0])
      .isEqualTo(exposedParam)
      .isNotEqualTo("param")
      .isNotEqualTo(null);

    var paramDiffName = new SonarLintExtendedLanguageClient.RuleParameter("other", exposedParam.description, exposedParam.defaultValue);
    assertThat(exposedParam.hashCode()).isNotEqualTo(paramDiffName.hashCode());
    assertThat(exposedParam).isNotEqualTo(paramDiffName);

    var paramDiffDesc = new SonarLintExtendedLanguageClient.RuleParameter(exposedParam.name, "other", exposedParam.defaultValue);
    assertThat(exposedParam.hashCode()).isNotEqualTo(paramDiffDesc.hashCode());
    assertThat(exposedParam).isNotEqualTo(paramDiffDesc);

    var paramDiffDefaultValue = new SonarLintExtendedLanguageClient.RuleParameter(exposedParam.name, exposedParam.description, "other");
    assertThat(exposedParam.hashCode()).isNotEqualTo(paramDiffDefaultValue.hashCode());
    assertThat(exposedParam).isNotEqualTo(paramDiffDefaultValue);

    var ruleDescTaintParams = new ShowRuleDescriptionParams("javasecurity:S1234", "name1", "desc1", ruleDescTabs, RuleType.VULNERABILITY, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.emptyList(), CleanCodeAttribute.TRUSTWORTHY.getLabel(), CleanCodeAttributeCategory.RESPONSIBLE.getLabel(), Map.of(SoftwareQuality.SECURITY.getLabel(), ImpactSeverity.HIGH.getLabel()));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescTaintParams.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescTaintParams);
  }

  @Test
  void test_rule_description_non_contextual_tabs_equals_hashCode() {
    var descTab = new SonarLintExtendedLanguageClient.RuleDescriptionTab("title", new SonarLintExtendedLanguageClient.RuleDescriptionTabNonContextual("desc"));
    var descTabSame = new SonarLintExtendedLanguageClient.RuleDescriptionTab("title", new SonarLintExtendedLanguageClient.RuleDescriptionTabNonContextual("desc"));

    assertThat(descTab)
      .isEqualTo(descTab)
      .isEqualTo(descTabSame)
      .hasSameHashCodeAs(descTabSame)
      .isNotEqualTo(null)
      .isNotEqualTo(new Object());

    var descTabDiffTitle = new SonarLintExtendedLanguageClient.RuleDescriptionTab("anotherTitle", new SonarLintExtendedLanguageClient.RuleDescriptionTabNonContextual("desc"));
    assertThat(descTab).isNotEqualTo(descTabDiffTitle);
    assertThat(descTab.hashCode()).isNotEqualTo(descTabDiffTitle.hashCode());

    var descTabDiffDesc = new SonarLintExtendedLanguageClient.RuleDescriptionTab("title", new SonarLintExtendedLanguageClient.RuleDescriptionTabNonContextual("anotherDesc"));
    assertThat(descTab).isNotEqualTo(descTabDiffDesc);
    assertThat(descTab.hashCode()).isNotEqualTo(descTabDiffDesc.hashCode());
  }

  @Test
  void test_rule_description_contextual_tabs_equals_hashCode() {
    var descTab = new SonarLintExtendedLanguageClient.RuleDescriptionTab("title",
      new SonarLintExtendedLanguageClient.RuleDescriptionTabContextual[]{
        new SonarLintExtendedLanguageClient.RuleDescriptionTabContextual("desc", "java", "java")
      }, "java"
    );
    var descTabSame = new SonarLintExtendedLanguageClient.RuleDescriptionTab("title",
      new SonarLintExtendedLanguageClient.RuleDescriptionTabContextual[]{
        new SonarLintExtendedLanguageClient.RuleDescriptionTabContextual("desc", "java", "java")
      }, "java"
    );
    assertThat(descTab)
      .isEqualTo(descTab)
      .isEqualTo(descTabSame)
      .hasSameHashCodeAs(descTabSame)
      .isNotEqualTo(null)
      .isNotEqualTo(new Object());

    var descTabDiffTitle = new SonarLintExtendedLanguageClient.RuleDescriptionTab("anotherTitle",
      new SonarLintExtendedLanguageClient.RuleDescriptionTabContextual[]{
        new SonarLintExtendedLanguageClient.RuleDescriptionTabContextual("desc", "java", "java")
      }, "java"
    );
    assertThat(descTab).isNotEqualTo(descTabDiffTitle);
    assertThat(descTab.hashCode()).isNotEqualTo(descTabDiffTitle.hashCode());

    var descTabDiffDesc = new SonarLintExtendedLanguageClient.RuleDescriptionTab("title",
      new SonarLintExtendedLanguageClient.RuleDescriptionTabContextual[]{
        new SonarLintExtendedLanguageClient.RuleDescriptionTabContextual("desc1", "java", "java")
      }, "java"
    );
    assertThat(descTab).isNotEqualTo(descTabDiffDesc);
    assertThat(descTab.hashCode()).isNotEqualTo(descTabDiffDesc.hashCode());

    var descTabDiffContextKey = new SonarLintExtendedLanguageClient.RuleDescriptionTab("title",
      new SonarLintExtendedLanguageClient.RuleDescriptionTabContextual[]{
        new SonarLintExtendedLanguageClient.RuleDescriptionTabContextual("desc", "servlet", "Servlet")
      }, "servlet"
    );
    assertThat(descTab).isNotEqualTo(descTabDiffContextKey);
    assertThat(descTab.hashCode()).isNotEqualTo(descTabDiffContextKey.hashCode());
  }

  @Test
  void test_project_branch_equals_hashCode() {
    var underTest = SonarLintExtendedLanguageClient.ReferenceBranchForFolder.of("file:///some/uri", "branch/name");

    var otherFolder = SonarLintExtendedLanguageClient.ReferenceBranchForFolder.of("file:///some/other/uri", "branch/name");
    var otherBranch = SonarLintExtendedLanguageClient.ReferenceBranchForFolder.of("file:///some/uri", "other/branch");

    assertThat(underTest)
      .isNotEqualTo(null)
      .isNotEqualTo(new Object())
      .isEqualTo(underTest)
      .isEqualTo(SonarLintExtendedLanguageClient.ReferenceBranchForFolder.of("file:///some/uri", "branch/name"))
      .hasSameHashCodeAs(SonarLintExtendedLanguageClient.ReferenceBranchForFolder.of("file:///some/uri", "branch/name"))
      .isNotEqualTo(otherFolder)
      .doesNotHaveSameHashCodeAs(otherFolder)
      .isNotEqualTo(otherBranch)
      .doesNotHaveSameHashCodeAs(otherBranch);
  }

  @Test
  void test_report_connection_check_success() {
    String connectionId = "connectionId";
    var underTest = SonarLintExtendedLanguageClient.ConnectionCheckResult.success(connectionId);
    assertThat(underTest.getConnectionId()).isEqualTo(connectionId);
    assertThat(underTest.isSuccess()).isTrue();
    assertThat(underTest.getReason()).isNull();
  }


  @Test
  void test_report_connection_check_failure() {
    String connectionId = "connectionId";
    String reason = "reason";
    var underTest = SonarLintExtendedLanguageClient.ConnectionCheckResult.failure(connectionId, reason);
    assertThat(underTest.getConnectionId()).isEqualTo(connectionId);
    assertThat(underTest.isSuccess()).isFalse();
    assertThat(underTest.getReason()).isEqualTo(reason);
  }

  @Test
  void test_rule_description_params_is_taint() {
    var taint = new ShowRuleDescriptionParams("javasecurity:S5168", "Rule Name", null, null, RuleType.VULNERABILITY, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.emptyList(), "", "", Collections.emptyMap());
    var notTaint1 = new ShowRuleDescriptionParams("java:S5168", "Rule Name", null, null, RuleType.VULNERABILITY, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.emptyList(), "", "", Collections.emptyMap());
    var notTaint2 = new ShowRuleDescriptionParams("javasecurity:S5168", "Rule Name", null, null, RuleType.BUG, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.emptyList(), "", "", Collections.emptyMap());

    assertThat(taint.isTaint()).isTrue();
    assertThat(notTaint1.isTaint()).isFalse();
    assertThat(notTaint2.isTaint()).isFalse();
  }

  @Test
  void test_rule_description_language() {
    var ruleDesc1 = new ShowRuleDescriptionParams("javasecurity:S5168", "Rule Name", null, null, RuleType.VULNERABILITY, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.emptyList(), "", "", Collections.emptyMap());
    var ruleDesc2 = new ShowRuleDescriptionParams("java:S5168", "Rule Name", null, null, RuleType.VULNERABILITY, Language.JAVA.getLanguageKey(), IssueSeverity.BLOCKER, Collections.emptyList(), "", "", Collections.emptyMap());

    assertThat(ruleDesc1.getLanguageKey()).isEqualTo(Language.JAVA.getLanguageKey());
    assertThat(ruleDesc2.getLanguageKey()).isEqualTo(Language.JAVA.getLanguageKey());
  }

  @Test
  void test_find_file_in_folder_equals_hashCode() {
    var findFileByNamesInFolder = new SonarLintExtendedLanguageClient
      .FindFileByNamesInFolder("folderUri1", Collections.singletonList("file1"));
    var findFileByNamesInFolderSame = new SonarLintExtendedLanguageClient
      .FindFileByNamesInFolder("folderUri1", Collections.singletonList("file1"));
    var findFileByNamesInFolderDifferent = new SonarLintExtendedLanguageClient
      .FindFileByNamesInFolder("folderUri1", Collections.singletonList("file2"));

    assertThat(findFileByNamesInFolder)
      .isEqualTo(findFileByNamesInFolder)
      .isEqualTo(findFileByNamesInFolderSame)
      .hasSameHashCodeAs(findFileByNamesInFolderSame)
      .isNotEqualTo(null)
      .isNotEqualTo(new Object())
      .isNotEqualTo(findFileByNamesInFolderDifferent)
      .doesNotHaveSameHashCodeAs(findFileByNamesInFolderDifferent);

    assertThat(findFileByNamesInFolder.getFolderUri()).isEqualTo(findFileByNamesInFolderSame.getFolderUri());
    assertThat(findFileByNamesInFolder.getFilenames()).isEqualTo(findFileByNamesInFolderSame.getFilenames());
  }
}
