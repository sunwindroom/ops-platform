#!/bin/bash
curl -s -X POST http://localhost/api/account/login/ -H 'Content-Type: application/json' -d '{"username":"admin","password":"spug.cc","type":"default"}'