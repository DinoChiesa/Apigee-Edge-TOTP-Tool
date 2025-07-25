// Copyright © 2025 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.google.dchiesa;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.google.dchiesa.encoding.Base16;
import com.google.dchiesa.encoding.Base32;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;

public class EdgeTotp {

  public EdgeTotp(String[] args) throws java.lang.Exception {
    getOpts(args);
  }

  enum ArgState {
    NEXTARG,
    FAKETIME,
    SECRETKEY,
    THRESHOLD,
    NDIGITS,
    HASHFN,
    DONE
  }

  private static final String DEFAULT_HASH_FUNCTION = "HmacSHA1";
  private static final int DEFAULT_TIME_STEP_SECONDS = 30;
  private static final int DEFAULT_CODE_DIGITS = 6;
  private static final int DEFAULT_TIME_REMAINING_THRESHOLD = 3;

  private String secretKey;
  private Instant instant;
  private String hashFunctionIdentifier;
  private int codeDigits = 0;
  private int delta = 0;
  private int secondsRemaining = 0;
  private int timeStepSizeInSeconds = DEFAULT_TIME_STEP_SECONDS;
  private boolean wantQuiet = false;

  private static String readLineFromStdin() {
    System.err.println("Reading password from stdin...");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      String oneLine = reader.readLine();
      if (oneLine != null && !oneLine.isEmpty()) {
        return oneLine;
      } else {
        return null;
      }
    } catch (IOException e) {
      throw new RuntimeException("Error reading from stdin.", e);
    }
  }

  private String resolveHashFunctionId(String value) {
    if (value == null) return DEFAULT_HASH_FUNCTION;
    value = value.toLowerCase();
    if ("sha1".equals(value) || "hmacsha1".equals(value)) return "HmacSHA1";
    if ("sha256".equals(value) || "hmacsha256".equals(value)) return "HmacSHA256";
    if ("sha512".equals(value) || "hmacsha512".equals(value)) return "HmacSHA512";
    return DEFAULT_HASH_FUNCTION;
  }

  private byte[] decodeKey(String encodedKey, String encoding) {
    if ("hex".equals(encoding) || "base16".equals(encoding)) {
      return Base16.decode(encodedKey);
    }
    if ("base32".equals(encoding)) {
      return Base32.decode(encodedKey);
    }
    if ("base64url".equals(encoding)) {
      return Base64.getUrlDecoder().decode(encodedKey);
    }
    if ("base64".equals(encoding)) {
      return Base64.getDecoder().decode(encodedKey);
    }
    // utf-8 encoded
    return encodedKey.getBytes(StandardCharsets.UTF_8);
  }

  private Instant getInstant() {
    instant = Instant.now();
    secondsRemaining = 30 - (int) (instant.getEpochSecond() % timeStepSizeInSeconds);
    if (secondsRemaining <= delta) {
      try {
        Thread.sleep(1000 * (secondsRemaining + 1));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // Restore the interrupted status
        System.out.println("Thread was interrupted: " + e.getMessage());
      }
    }
    instant = Instant.now();
    secondsRemaining = 30 - (int) (instant.getEpochSecond() % timeStepSizeInSeconds);
    return instant;
  }

  private void getOpts(String[] args) throws java.lang.Exception {
    if ((args != null) && (args.length != 0)) {

      ArgState state = ArgState.NEXTARG;

      int L = args.length;

      for (int i = 0; i < L; i++) {
        String arg = args[i].trim();
        switch (state) {
          case NEXTARG:
            if (arg.equals("-h") || arg.equals("--help")) {
              usage();
              System.exit(0);
            } else if (arg.equals("-f") || arg.equals("--fake-time")) {
              state = ArgState.FAKETIME;
            } else if (arg.equals("-k") || arg.equals("--secretkey")) {
              state = ArgState.SECRETKEY;
            } else if (arg.equals("-h") || arg.equals("--hash")) {
              state = ArgState.HASHFN;
            } else if (arg.equals("-n") || arg.equals("--ndigits")) {
              state = ArgState.NDIGITS;
            } else if (arg.equals("-t") || arg.equals("--time-threshold")) {
              state = ArgState.THRESHOLD;
            } else if (arg.equals("-q") || arg.equals("--quiet")) {
              state = ArgState.NEXTARG;
              wantQuiet = true;
            } else {
              throw new IllegalArgumentException(
                  String.format("argument (%s) is not recognized.", arg));
            }
            break;

          case FAKETIME:
            if (instant != null) {
              throw new RuntimeException(
                  String.format("You already specified that argument (%s).", args[i - 1]));
            }
            try {
              instant = Instant.ofEpochMilli(Long.parseLong(arg));
            } catch (Exception e) {
              throw new RuntimeException("Error resolving time value.", e);
            }
            state = ArgState.NEXTARG;
            break;

          case HASHFN:
            if (hashFunctionIdentifier != null) {
              throw new RuntimeException(
                  String.format("You already specified that argument (%s).", args[i - 1]));
            }
            hashFunctionIdentifier = resolveHashFunctionId(arg);
            state = ArgState.NEXTARG;
            break;

          case SECRETKEY:
            if (secretKey != null) {
              throw new RuntimeException(
                  String.format("You already specified that argument (%s).", args[i - 1]));
            }
            if (arg.equals("-")) {
              secretKey = readLineFromStdin();
            } else {
              secretKey = arg;
            }
            state = ArgState.NEXTARG;
            break;

          case NDIGITS:
            if (codeDigits != 0) {
              throw new RuntimeException(
                  String.format("You already specified that argument (%s).", args[i - 1]));
            }
            try {
              codeDigits = Integer.parseInt(arg);
            } catch (Exception e) {
              throw new RuntimeException("Error resolving digits.", e);
            }
            state = ArgState.NEXTARG;
            break;

          case THRESHOLD:
            if (delta != 0) {
              throw new RuntimeException(
                  String.format("You already specified that argument (%s).", args[i - 1]));
            }
            try {
              delta = Integer.parseInt(arg);
              if (delta > 20 || delta < 1) {
                throw new RuntimeException("Cmon dude, specify a reasonable value.");
              }
            } catch (Exception e) {
              throw new RuntimeException("Error resolving time threshold.", e);
            }
            state = ArgState.NEXTARG;
            break;

          case DONE:
            throw new IllegalArgumentException(
                String.format("argument (%s) is invalid here.", arg));

          default:
            throw new IllegalStateException("invalid state while processing arguments.");
        }
      }
      if (state != ArgState.NEXTARG) {
        throw new IllegalStateException("incomplete arguments.");
      }
    } else {
      System.out.printf("no arguments!\n");
    }
    if (secretKey == null) {
      throw new IllegalStateException("you must provide a secret.");
    }
    if (delta == 0) {
      delta = DEFAULT_TIME_REMAINING_THRESHOLD;
    }
    if (instant == null) {
      instant = getInstant();
    }
    if (codeDigits == 0) {
      codeDigits = DEFAULT_CODE_DIGITS;
    }
    if (hashFunctionIdentifier == null) {
      hashFunctionIdentifier = DEFAULT_HASH_FUNCTION;
    }
  }

  public void Run() throws Exception {
    TimeBasedOneTimePasswordGenerator totp =
        new TimeBasedOneTimePasswordGenerator(
            Duration.ofSeconds(timeStepSizeInSeconds), codeDigits, hashFunctionIdentifier);

    final Key key = new SecretKeySpec(decodeKey(secretKey, "base32"), "RAW");
    if (wantQuiet) {
      System.out.printf("%s\n", totp.generateOneTimePasswordString(key, instant));
    } else {
      final Instant later = instant.plus(totp.getTimeStep());
      System.out.printf("Current: %s\n", totp.generateOneTimePasswordString(key, instant));
      System.out.printf("Seconds remaining: %d\n", secondsRemaining);
      System.out.printf("Next: %s\n", totp.generateOneTimePasswordString(key, later));
    }
  }

  public static void usage() {
    System.out.println("EdgeTotp: generate a TOTP.\n");
    System.out.println("Usage:\n  java EdgeTotp [options]");
    System.out.println(
        "    -k, --secretKey      SECRET       required. secret key. use - to read the key from"
            + " stdin.");
    System.out.println(
        "    -h, --hash           HASH         optional. hash function. sha1 or sha256. Default"
            + " sha256.");
    System.out.println(
        "    -n, --ndigits        DIGITS       optional. Number of digits of output.");
    System.out.println(
        "    -f, --fake-time      MILLIS       optional. fake time milliseconds. For testing"
            + " only.");
    System.out.println(
        "    -t, --time-threshold SECS         optional. If <= this many seconds remain, delay and"
            + " wait until the next time step before generating the code.");
    System.out.println("    -q, --quiet                       display only the code as output.");
  }

  public static void main(String[] args) {
    try {
      EdgeTotp me = new EdgeTotp(args);
      me.Run();
    } catch (java.lang.Exception exc1) {
      System.out.println("Exception:" + exc1.toString());
      exc1.printStackTrace(System.out);
      usage();
    }
  }
}
