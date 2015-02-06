var Dropbox = {};

Dropbox.Client = function() {};
Dropbox.Client.isAuthenticated = function () {};
Dropbox.Client.authenticate = function(options, callback) {};
Dropbox.Client.getDatastoreManager = function() {};
Dropbox.Client.signOut = function(options, callback) {};
Dropbox.Client.readFile = function(path, options, callback) {};

Dropbox.Datastore.DatastoreManager.openDefaultDatastore = function(callback) {};

Dropbox.Datastore.getTable = function(tableId) {};

Dropbox.Datastore.Table.query = function(fieldValues) {};
Dropbox.Datastore.Table.insert = function(index, value) {};
