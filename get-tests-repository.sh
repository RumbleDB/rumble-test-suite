#!/bin/sh

TESTS_DIRECTORY="qt3tests"

if [ ! -d $TESTS_DIRECTORY ]; then
    echo "Cloning repository that contains all tests..."
    git clone https://github.com/w3c/qt3tests
    echo "Cloning repository complete!"
else
    echo "Pulling latest..."
    cd qt3tests/
    git pull
    cd ..
    echo "Pulling latest complete!"
fi

echo "Tests repository is in directory:"
echo $TESTS_DIRECTORY
