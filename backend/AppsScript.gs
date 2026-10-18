/**
 * CardDrop backend - deploy this as a Google Apps Script Web App.
 * It receives one contact's details from the Android app (as JSON) and sends
 * a personalized email from YOUR Gmail account using GmailApp. No server to
 * host, no billing, uses the Gmail account you deploy it with.
 *
 * SETUP (about 2 minutes):
 * 1. Go to https://script.google.com -> New project.
 * 2. Delete the placeholder "myFunction" code and paste this whole file in.
 * 3. Change SHARED_SECRET below to a random string of your own choosing.
 * 4. Click Deploy -> New deployment -> gear icon -> select type "Web app".
 *      Description:   whatever you like
 *      Execute as:    Me
 *      Who has access: Anyone
 *    (It has to be "Anyone" because the Android app calls it without a
 *    Google login - that's what SHARED_SECRET is for: anyone else calling
 *    the URL without the matching secret gets an "unauthorized" response
 *    instead of triggering a send.)
 * 5. Click Deploy, then Authorize access -> pick your Google account ->
 *    Advanced -> "Go to CardDrop (unsafe)" -> Allow. (This warning shows up
 *    because the script isn't published by Google - it's fine, it's your
 *    own script running under your own account.)
 * 6. Copy the "Web app URL" it gives you (ends in /exec) and paste it, along
 *    with the same secret, into the CardDrop app's Settings screen.
 * 7. Run testSendToSelf() once from this editor (pick it in the function
 *    dropdown up top, click Run) to confirm sending works end-to-end before
 *    relying on it tomorrow. Check your inbox.
 *
 * If you ever edit this script after deploying, use Deploy -> Manage
 * deployments -> edit (pencil) -> New version, otherwise the live URL keeps
 * running the old code.
 */

var SHARED_SECRET = "change-this-to-a-random-string";

function doPost(e) {
  var result = { status: "ok" };
  try {
    var data = JSON.parse(e.postData.contents);

    if (data.secret !== SHARED_SECRET) {
      return jsonResponse({ status: "error", message: "unauthorized" });
    }
    if (!data.email) {
      return jsonResponse({ status: "error", message: "missing email address" });
    }

    var subject = buildSubject(data);
    var body = buildBody(data);

    GmailApp.sendEmail(data.email, subject, body, {
      name: data.senderName || Session.getActiveUser().getEmail()
    });

    result.message = "sent to " + data.email;
  } catch (err) {
    result = { status: "error", message: String(err) };
  }
  return jsonResponse(result);
}

function buildSubject(data) {
  var event = data.eventName ? " at " + data.eventName : "";
  return "Great connecting" + event + (data.name ? ", " + data.name : "") + "!";
}

function buildBody(data) {
  var greetingName = data.name ? data.name : "there";
  var event = data.eventName ? " at " + data.eventName : " today";
  var sender = data.senderName || "";
  var lines = [];

  lines.push("Hi " + greetingName + ",");
  lines.push("");
  lines.push("It was great meeting you" + event + ". I'm following up so we have");
  lines.push("each other's details on hand - happy to stay in touch.");
  lines.push("");

  if (data.company || data.title) {
    var role = [data.title, data.company].filter(Boolean).join(" at ");
    lines.push("(Noted you're " + role + ".)");
    lines.push("");
  }

  lines.push("Best,");
  lines.push(sender);

  return lines.join("\n");
}

function jsonResponse(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

/**
 * Sanity check: run this once from the editor (select testSendToSelf in the
 * function dropdown, click Run) to send yourself a test email and confirm
 * both the secret and Gmail authorization are working before tomorrow.
 */
function testSendToSelf() {
  var fakeEvent = {
    postData: {
      contents: JSON.stringify({
        secret: SHARED_SECRET,
        name: "Test Contact",
        company: "Example Co",
        title: "Tester",
        email: Session.getActiveUser().getEmail(),
        senderName: "You",
        eventName: "the test run"
      })
    }
  };
  Logger.log(doPost(fakeEvent).getContent());
}
