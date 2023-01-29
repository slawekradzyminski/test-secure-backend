#!/bin/bash
if [ -e ./target/jwt-auth-service-1.0.0.jar ]; then
  echo "File exists"
else
  echo "File does not exist"
  exit 1
fi