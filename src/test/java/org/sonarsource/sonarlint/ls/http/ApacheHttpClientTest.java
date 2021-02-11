package org.sonarsource.sonarlint.ls.http;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

class ApacheHttpClientTest {

  ApacheHttpClient underTest = ApacheHttpClient.create();

  @Test
  void get_request_test() {
    HttpClient.Response response = underTest.get("http://sonarsource.com");
    String responseString = response.bodyAsString();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(responseString).isNotEmpty();
  }

  @Test
  void post_request_test() {
    HttpClient.Response response = underTest.post("http://sonarsource.com", "image/jpeg", "");
    String responseString = response.bodyAsString();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(responseString).isNotEmpty();
  }

  @Test
  void delete_request_test() {
    HttpClient.Response response = underTest.delete("http://sonarsource.com", "image/jpeg", "");
    String responseString = response.bodyAsString();

    assertThat(response.isSuccessful()).isFalse();
    assertThat(responseString).isNotEmpty();
  }

}
