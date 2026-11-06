package mi.tiktokmi;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

/**
 * This phone's key for the owner's admin, and requests signed with it.
 *
 * The key is made in the phone's keystore and never leaves it: the server is
 * shown the public half once, the owner approves it with a code from
 * Telegram, and from then on every request carries the account's token and
 * a signature over the request itself, its time and a number used once.
 * The server checks all three (server/admin_mod.py); nothing here is trusted
 * on its own.
 */
final class AdminDevice {
    private AdminDevice() {}

    private static final String ALIAS = "margyt_admin";

    /** What the server says when a request is refused, so the sheet can say why. */
    static final class Refused extends Exception {
        final String why;
        Refused(String why) { super(why); this.why = why; }
    }

    private static KeyStore store() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator maker = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            maker.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build());
            maker.generateKeyPair();
        }
        return store;
    }

    private static byte[] publicKey() throws Exception {
        return store().getCertificate(ALIAS).getPublicKey().getEncoded();
    }

    /** The name the server knows this phone by: the start of its key's hash. */
    static String id() throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(publicKey());
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 12; i++) out.append(String.format("%02x", hash[i] & 0xFF));
        return out.toString();
    }

    /** Ask for this phone to be the owner's. "approved", or "code" when one went to Telegram. */
    static String register() throws Exception {
        String uid = Account.id(), token = Mine.token();
        if (uid == null || token.length() == 0) throw new Refused("prove");
        JSONObject body = new JSONObject()
                .put("uid", uid).put("token", token)
                .put("key", Base64.encodeToString(publicKey(), Base64.NO_WRAP))
                .put("name", (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim());
        return answer(Net.talk(Badges.SERVER + "/mod/device", body.toString())).optString("state", "");
    }

    /** One signed request; the answer, or Refused with the server's reason. */
    static JSONObject act(String op, JSONObject args) throws Exception {
        String uid = Account.id(), token = Mine.token();
        if (uid == null || token.length() == 0) throw new Refused("prove");
        byte[] once = new byte[16];
        new SecureRandom().nextBytes(once);
        StringBuilder nonce = new StringBuilder();
        for (byte b : once) nonce.append(String.format("%02x", b & 0xFF));
        JSONObject said = args == null ? new JSONObject() : new JSONObject(args.toString());
        said.put("uid", uid).put("token", token).put("device", id()).put("op", op)
                .put("at", System.currentTimeMillis() / 1000L).put("nonce", nonce.toString());
        String text = said.toString();
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign((PrivateKey) store().getKey(ALIAS, null));
        signer.update(text.getBytes("UTF-8"));
        JSONObject body = new JSONObject().put("signed", text)
                .put("sig", Base64.encodeToString(signer.sign(), Base64.NO_WRAP));
        return answer(Net.talk(Badges.SERVER + "/mod/admin", body.toString()));
    }

    private static JSONObject answer(Net.Said said) throws Exception {
        JSONObject out;
        try {
            out = new JSONObject(said.body == null || said.body.length() == 0 ? "{}" : said.body);
        } catch (Throwable unreadable) {
            out = new JSONObject();
        }
        if (!said.ok()) throw new Refused(out.optString("error", "net"));
        return out;
    }
}
