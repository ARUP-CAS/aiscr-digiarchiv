#!/usr/bin/python

import json
import mechanize
import re
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

def main():
    config_file = '/var/lib/archeo/amcr/config.json'
    host = 'localhost'

    config = load_config(config_file)
    endpoint = host + ':8080'

    login_args = { 'action': 'LOGIN' }
    for key in ( 'user', 'pwd' ):
        login_args[key] = config[key]

    login_url = build_url(endpoint, 'login', login_args)

    br = mechanize.Browser()
    print login_url
    response = br.open(login_url)
    if response.code != 200:
        print response.read()
        raise Exception("login failed, with code %d" % response.code)

    index_url = build_url(endpoint, 'indexer', { 'action': 'FULL', 'clean': 'true' })
    print index_url
    response = br.open(index_url)
    print response.read()

if __name__ == '__main__':
    main()
