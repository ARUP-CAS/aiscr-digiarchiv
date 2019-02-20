#!/usr/bin/python

import argparse
import collections
from email.mime.base import MIMEBase
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
import json
import mechanize
import os.path
import re
import smtplib
import sys
import urllib
import urlparse

def clean_json(s):
    s = re.sub(",[ \t\r\n]+}", "}", s)
    s = re.sub(",[ \t\r\n]+\]", "]", s)

    return s

def load_config(config_file):
    with open(config_file) as f:
        s = clean_json(f.read())
        doc = json.loads(s)
        server = doc['server']
        return server['amcrapi']

def build_url(endpoint, path, args):
    parts = ( 'http', endpoint, path, '', urllib.urlencode(args), '' )
    return urlparse.urlunparse(parts)

def handle_error_and_exit(recipient, send_flag, err_msg, err_response = None):
    sender = "digindex@digiarchiv.amapa.cz"
    outer = MIMEMultipart()
    outer['Subject'] = "Chyba indexace / Indexing error"
    outer['To'] = recipient
    outer['From'] = sender
    outer.preamble = 'You will not see this in a MIME-aware mail reader.\n'

    text = MIMEText(err_msg)
    outer.attach(text)

    if err_response:
        atta = MIMEBase('application', 'json')
        atta.add_header('Content-Disposition', 'attachment; filename="%s"' % os.path.basename(err_response))
        with open(err_response, 'rb') as f:
            atta.set_payload(f.read())

        outer.attach(atta)

    serialized = outer.as_string()
    print serialized

    if send_flag:
        proxy = smtplib.SMTP('localhost')
        proxy.sendmail(sender, [ recipient ], serialized)
        proxy.quit()

    sys.exit(0)

def open_url(br, url, url_name, recipient, dump_always, dump_file_name):
    print url
    try:
        response = br.open(url)
    except Exception as ex:
        open_error = "cannot open %s URL: %s" % (url_name, ex)
        handle_error_and_exit(recipient, recipient, open_error)

    rsp_path = None
    if dump_always or (response.code != 200):
        rsp_path = os.path.join('/tmp', dump_file_name)
        with open(rsp_path, 'w+') as f:
            f.write(response.read())

        if response.code != 200:
            response_error = "%s failed, with code %d" % (url_name, response.code)
            handle_error_and_exit(recipient, recipient, response_error, rsp_path)

    return rsp_path

def has_errors(doc):
    if isinstance(doc, dict):
        for k, v in doc.iteritems():
            if ((k == 'error') or ((k == 'errors') and v)) or has_errors(v):
                return True
    elif isinstance(doc, collections.Sequence) and not isinstance(doc, basestring):
        for it in doc:
            if has_errors(it):
                return True
    else:
        return False

def main():
    parser = argparse.ArgumentParser(description='Invoke Digital archive indexing from command line.')
    parser.add_argument('--host', default='localhost', help="Digital archive server")
    parser.add_argument('--recipient', '-r', default='', help="where to send e-mail on failure")
    parser.add_argument('--clean', action='store_true', help="empty database before re-index")
    parser.add_argument('--no-index', '-n', action='store_true', help="don't index, just send e-mail")
    parser.add_argument('--config', default='/var/lib/archeo/amcr/config.json', help="config file path")
    args = parser.parse_args()

    config_file = args.config
    config = load_config(config_file)
    recipient = config.get("recipient")
    if args.recipient:
        recipient = args.recipient

    host = args.host
    recipient = args.recipient
    clean_flag = args.clean

    if args.no_index:
        handle_error_and_exit(recipient, recipient, "This is a test. This is only a test.")

    endpoint = host + ':8080'

    br = mechanize.Browser()

    if host != 'localhost':
        login_args = { 'action': 'LOGIN' }
        for key in ( 'user', 'pwd' ):
            login_args[key] = config[key]

        login_url = build_url(endpoint, 'login', login_args)
        open_url(br, login_url, "login", recipient, False, "login.html")

    index_args = { 'action': 'FULL' }
    if clean_flag:
        index_args['clean'] = 'true'

    index_url = build_url(endpoint, 'indexer', index_args)
    fname = open_url(br, index_url, "indexer", recipient, True, "indexer.json")

    with open(fname) as f:
        rsp_doc = json.load(f)

    if has_errors(rsp_doc):
        handle_error_and_exit(recipient, recipient, "Errors found in indexer response.", fname)

if __name__ == '__main__':
    main()
