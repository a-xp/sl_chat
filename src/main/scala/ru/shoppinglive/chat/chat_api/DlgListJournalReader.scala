package ru.shoppinglive.chat.chat_api

import akka.actor.ActorRef
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import scaldi.{Injectable, Injector}

/**
  * Created by rkhabibullin on 20.12.2016.
  */


class DlgListJournalReader(implicit inj:Injector) extends Injectable{

  def readTo(to:ActorRef):Unit = {
      val url = MongoClientURI( inject [String] ("akka.contrib.persistence.mongodb.mongo.mongouri"))
      val client = MongoClient(url)
      val db = client(url.database.get)
      val collection = db("akka_persistence_journal")

      collection.find( MongoDBObject("_h" -> "ru.shoppinglive.chat.chat_api.Event$DialogCreated")) foreach( dbo =>
        println(dbo)
      )
  }

}
