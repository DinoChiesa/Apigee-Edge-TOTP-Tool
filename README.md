# Apigee Edge TOTP Generator

This directory contains a command-line tool to generate a Time-based One-time
Password (TOTP), as described in [IETF RFC 6238](https://tools.ietf.org/html/rfc6238).

This TOTP could be used as the 2nd factor when signing in to Apigee Edge.

This will be useful to you if:
- you have Apigee Edge SaaS
- you do not have SAML configured
- you use Apigee-provided 2FA on your account
- you have access to the 2FA _secret_
- you want a way to generate a TOTP in an automated way

In the normal case, the TOTP is assisted by an "Authenticator" app that you
install on your mobile phone. There are a number of suppliers of such apps; they
all work the same way and are based on the same TOTP standard.


## Building it

Requires:
- mvn 3.9.x
- Java11. I haven't tested on later versions of Java.

```console
mvn clean package
```

## Using it

The TOTP process works with a secret key that is shared across the server side
and your authenticator app.  To use this tool, you must have the access to this
originating "seed" key.

Normally, when you set up 2FA with Apigee, or any other system that uses TOTP,
you will see a barcode displayed on the screen.  The typical process is to scan
the barcode with the camera on your phone, and that will automatically load the
secret into your Authenticator app. You never see the secret, aside from that
barcode.

Once you have loaded the secret into your Authenticator app, there is no way to
extract the originating secret. So if you already have 2FA set up with Apigee,
it's too late; you cannot use this tool.

If you want to use this command, you must first _unlink_ that secret from your
Apigee account, then initiate a new 2FA secret. Be aware: Unlinking means, whatever mobile app 
you have set up, will no longer work as a supplier of TOTP for Edge. 

![Set up 2FA in Apigee Edge](./img/set-up-2fa-apigee.png)

When you see the barcode, click the text that reads "xxx yyy zzz".

![the barcode](./img/link-a-new-device-via-barcode.png)


This will display a string of characters; that is your TOTP Secret. Copy THAT
string into a plain text file, and you can use it with this tool.

If you _ALSO_ want to set up a mobile app, copy the string of characters, and
then also scan the barcode with your mobile app.

If you have stored your secret into the file "secretkey.txt", then from a bash
shell you can use this command to generate the TOTP:

```sh
java --class-path ./target/lib/\*.jar  \
   -jar ./target/com.google.dchiesa-apigee-edge-totp-20250725.jar \
   -k $(<./secretkey.txt)
```

The output will be something like this:
```console
Current: 351395
Seconds remaining: 8
Next: 436006
```

The TOTP changes every 30 seconds. If you prefer to not get that information in the output, you
can pass the -q option, in which case, only the current password is displayed.


## About Information Security

The secret is a 2nd factor for authentication. It should be protected as if it
is a password.  It is not a password; it is the second factor.  To authenticate
to Edge SaaS, a person or system would need both the password of the account, as
well as the secret.  But it is sensitive material. Take appropriate precautions.

## Disclaimer

This example is not an official Google product, nor is it part of an
official Google product.

## License

This material is [Copyright © 2025 Google LLC](./NOTICE).
and is licensed under the [Apache 2.0 License](LICENSE).

