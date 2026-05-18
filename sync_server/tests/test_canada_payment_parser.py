"""Unit tests for the Interac email parser. Runs locally with pytest."""
from __future__ import annotations

import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from canada_payment_module import parse_interac_email  # noqa: E402


SAMPLE_INTERAC_HTML = """<html><body>
<table><tr><td>
<p>Hi HushTV,</p>
<p>Good news! John Smith sent you money.</p>
<table>
  <tr><td>Amount:</td><td>$40.00 (CAD)</td></tr>
  <tr><td>Sent From:</td><td>John Smith</td></tr>
  <tr><td>Sent On:</td><td>February 8, 2026 at 10:35 AM ET</td></tr>
  <tr><td>Message:</td><td>86879242</td></tr>
</table>
<p>The money has been automatically deposited into your account.</p>
</td></tr></table>
</body></html>"""


SAMPLE_INTERAC_HTML_LARGER_AMOUNT = """<html><body>
<p>Jane Doe sent you money.</p>
<p>Amount: $150.00 (CAD)<br>
Sent From: Jane Doe<br>
Message: 12345678</p>
</body></html>"""


SAMPLE_INTERAC_HTML_NO_MESSAGE = """<html><body>
<p>Amount: $40.00 (CAD)</p>
<p>Sent From: Anonymous</p>
</body></html>"""


SAMPLE_PLAINTEXT = """Hi HushTV,
Bob Johnson sent you money.
Amount: $40.00 (CAD)
Sent From: Bob Johnson
Sent On: February 9, 2026 at 4:12 PM ET
Message: 99887766
"""


def test_parse_basic_html():
    r = parse_interac_email(SAMPLE_INTERAC_HTML)
    assert r["amount_cad"] == 40.00, r
    assert r["order_id"] == "86879242", r
    assert r["sender_name"] == "John Smith", r


def test_parse_larger_amount():
    r = parse_interac_email(SAMPLE_INTERAC_HTML_LARGER_AMOUNT)
    assert r["amount_cad"] == 150.00
    assert r["order_id"] == "12345678"
    assert r["sender_name"] == "Jane Doe"


def test_parse_no_message_field_falls_back_to_any_8_digits():
    r = parse_interac_email(SAMPLE_INTERAC_HTML_NO_MESSAGE)
    assert r["amount_cad"] == 40.00
    # No Message: label, no order id available
    assert r["order_id"] is None


def test_parse_plaintext():
    r = parse_interac_email(SAMPLE_PLAINTEXT)
    assert r["amount_cad"] == 40.00
    assert r["order_id"] == "99887766"
    assert r["sender_name"] == "Bob Johnson"


def test_parse_amount_without_parentheses():
    r = parse_interac_email("Amount: $40.00 CAD\nMessage: 11223344")
    assert r["amount_cad"] == 40.00
    assert r["order_id"] == "11223344"


def test_parse_amount_with_extra_whitespace():
    r = parse_interac_email("Amount:  $ 40.00 (CAD) Message:   55667788")
    assert r["amount_cad"] == 40.00
    assert r["order_id"] == "55667788"


def test_html_with_message_in_table_cell():
    html = '<tr><td>Message:</td><td>  <strong>43219876</strong></td></tr>'
    r = parse_interac_email("Amount: $40.00 (CAD) " + html)
    assert r["order_id"] == "43219876"


if __name__ == "__main__":
    import subprocess, sys
    sys.exit(subprocess.call(["pytest", "-q", __file__]))
