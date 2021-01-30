package com.example.makeyourcs.data.firebase

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.MutableLiveData
import com.example.makeyourcs.data.AccountClass
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.ktx.Firebase
import io.reactivex.Completable
import java.lang.Boolean.FALSE
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.LocalDateTime

class   FirebaseAuthSource {
    val TAG = "FirebaseSource"
    val userDataLiveData = MutableLiveData<AccountClass>()
    val accountDataLiveData = MutableLiveData<List<AccountClass.SubClass>>()
    val followerWaitlistLiveData = MutableLiveData<List<AccountClass.Follower_wait_list>>()
    private val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }
    private val firestore: FirebaseFirestore by lazy{
        FirebaseFirestore.getInstance()
    }


    fun login(email: String, password: String) = Completable.create { emitter ->
        Log.d("TAG", email)
        firebaseAuth.signInWithEmailAndPassword(email!!, password).addOnCompleteListener {
            if (!emitter.isDisposed) {
                if (it.isSuccessful) {
//                    System.out.println("login : " + firebaseAuth.currentUser?.email)
                    emitter.onComplete()
                    userIdbyEmail()
                }
                else
                    emitter.onError(it.exception!!)
            }
        }
    }

    fun register(account: AccountClass) = Completable.create { emitter ->
        System.out.println(account)
        //https://inspirecoding.app/lessons/using-viewmodel/
        firestore.collection("Account").document(account.email.toString()).set(account)

        firebaseAuth.createUserWithEmailAndPassword(account.email.toString(), account.pw.toString()).addOnCompleteListener {
            if (!emitter.isDisposed) {
                if (it.isSuccessful) {
                    System.out.println("insert success")
                    userIdbyEmail()
                    emitter.onComplete()

                }
                else
                    emitter.onError(it.exception!!)
            }
        }
    }

    fun logout() = firebaseAuth.signOut()

    fun currentUser() = firebaseAuth.currentUser

    fun deleteUser() {
        // [START delete_user]
        val user = Firebase.auth.currentUser!!

        user.delete()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "User account deleted.")
                }
            }
        // [END delete_user]
        //TODO: 사용자 부캐안에 게시글 삭제
    }
    fun userIdbyEmail() : String? {
        var userId:String? = null
        firestore.collection("Account")
            .whereEqualTo("email", currentUser()!!.email.toString())
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    userId = document.get("userId").toString()
                    Log.d(TAG, "userId get success "+userId)
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents: ", exception)
            }

        return userId
    }

    fun observeUserData() {
        try {
            firestore.collection("Account").whereEqualTo("email", currentUser()!!.email.toString()).addSnapshotListener{ value, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e)
                    return@addSnapshotListener
                }
                for (doc in value!!) {
                    doc?.let {
                        val data = it?.toObject(AccountClass::class.java)
                        System.out.println(data)
                        userDataLiveData.postValue(data)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user data", e)
        }
    }

    fun setOriginAccount(name:String, introduction:String, imageurl:String) { //TODO: 사진 url :default
        val OriginAccount = AccountClass.SubClass()
        OriginAccount.name = name
        OriginAccount.introduction = introduction
        OriginAccount.sub_num = 0
        OriginAccount.group_name = "default"
        OriginAccount.profile_pic_url = imageurl


        firestore.collection("Account")
            .document(currentUser()!!.email.toString())
            .collection("SubAccount")
            .document(OriginAccount.group_name.toString())
            .set(OriginAccount)
            .addOnSuccessListener {
                Log.d(TAG, "First subaccount insert complete")
                return@addOnSuccessListener
            }
            .addOnFailureListener { e -> Log.w(TAG, "Error writing document", e)
                    return@addOnFailureListener
            }
    }
    fun setSubAccount(subaccount_num:Int, name:String, group_name:String, introduction:String, imageurl:String) { //TODO: 사진 url :default
        val SubAccount = AccountClass.SubClass()
        SubAccount.name = name
        SubAccount.introduction = introduction
        SubAccount.sub_num = subaccount_num+1
        SubAccount.group_name = group_name
        SubAccount.profile_pic_url = imageurl

        val subaccount = firestore.collection("Account")
            .document(currentUser()!!.email.toString())
            .collection("SubAccount")
            .document(group_name)
        val account = firestore.collection("Account").document(currentUser()!!.email.toString())
        // Get a new write batch and commit all write operations
        firestore.runBatch { batch ->
            // Set the value of 'NYC'
            batch.set(subaccount, SubAccount)
            // Update the number of sub account
            batch.update(account, "sub_count", subaccount_num+1)
        }
            .addOnSuccessListener { Log.d(TAG, "Transaction success!") }
            .addOnFailureListener { e -> Log.w(TAG, "Transaction failure.", e) }

    }
    fun delSubAccount(group_name:String) {
        val subaccount = firestore
            .collection("Account")
            .document(currentUser()!!.email.toString())
            .collection("SubAccount")
            .document(group_name)
        val account = firestore.collection("Account")
            .document(currentUser()!!.email.toString())

        firestore.runTransaction { transaction ->

            val snapshot = transaction.get(account)
            val newSubcount = Integer.parseInt(snapshot.get("sub_count")!!.toString()) - 1
            // Update the number of sub account
            transaction.delete(subaccount)
            transaction.update(account, "sub_account", newSubcount)
        }
            .addOnSuccessListener { Log.d(TAG, "Transaction success!") }
            .addOnFailureListener { e -> Log.w(TAG, "Transaction failure.", e) }
    }

    fun observeAccountData() {
        var subaccountlist : ArrayList<AccountClass.SubClass> = arrayListOf()
        try {
            firestore.collection("Account")
                .document(currentUser()!!.email.toString())
                .collection("SubAccount")
                .orderBy("sub_num")
                .addSnapshotListener{ value, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e)
                    return@addSnapshotListener
                }
                    subaccountlist.clear()
                for (doc in value!!) {
                    doc?.let {
                        val data = it?.toObject(AccountClass.SubClass::class.java)
                        subaccountlist.add(data)
                    }
                }
                    accountDataLiveData.postValue(subaccountlist)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user data", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun follow(toEmail:String)
    {
        val from = AccountClass.FollowClass()
        from.to_account = toEmail
        from.to_account_sub = hashMapOf("default" to FALSE)

        val to = AccountClass.Follower_wait_list()
        to.follow_date = LocalDateTime.now()
        to.from_account = currentUser()!!.email.toString()

        val fromAccount = firestore.collection("Account")
            .document(currentUser()!!.email.toString())
            .collection("Follow")
            .document(toEmail)
        val toAccount = firestore.collection("Account")
            .document(toEmail)
            .collection("Follower_wait_list")
            .document(currentUser()!!.email.toString())

        firestore.runBatch { batch ->
            batch.set(toAccount, to)
            batch.set(fromAccount, from)
        }
            .addOnSuccessListener { Log.d(TAG, "Transaction success!") }
            .addOnFailureListener { e -> Log.w(TAG, "Transaction failure.", e) }

    }

    fun observefollowerWaitList()
    {
        try {
            firestore.collection("Account")
                .document(currentUser()!!.email.toString())
                .collection("Follower_wait_list")
                .addSnapshotListener{ value, e ->
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e)
                        return@addSnapshotListener
                    }
                    for (doc in value!!) {
                        doc?.let {
                            val data = it?.toObject(AccountClass.Follower_wait_list::class.java)
                            System.out.println(data)
                            followerWaitlistLiveData.postValue(listOf(data))
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting follower wait list", e)
        }

    }
    fun acceptfollow(fromEmail:String, group_name_list:List<String>)
    {
//        val fromAccount = firestore.collection("Account")
//            .document(fromEmail)
//            .collection("Follow")
//            .document(currentUser()!!.email.toString())
//        val toAccount_waitlist = firestore.collection("Account")
//            .document(currentUser()!!.email.toString())
//            .collection("Follower_wait_list")
//            .document(fromEmail)
//        var toAccount_default = firestore.collection("Account")
//            .document(currentUser()!!.email.toString())
//            .collection("SubAccount")
//            .document("default")
//
//        firestore.runTransaction { transaction ->
//            transaction.delete(toAccount_waitlist) // waitlist 에서 삭제
//            var default_sub_account = transaction.get(toAccount_default).toObject(AccountClass.SubClass::class.java)
//            default_sub_account?.follower_num = default_sub_account?.follower_num
//            default_sub_account?.follower[fromEmail] = true
//            //follwer_list 에 추가 해야함
//            transaction.update(toAccount_default, "follower_num", newfollower_num)
//            for (group_name in group_name_list){
//                var toAccount = firestore.collection("Account")
//                    .document(currentUser()!!.email.toString())
//                    .collection("SubAccount")
//                    .document(group_name)
//                snapshot = transaction.get(toAccount)
//                newfollower_num = Integer.parseInt(snapshot.get("follower_num")!!.toString()) + 1
//                transaction.update(toAccount, "follower_num", newfollower_num)
//
//            }
//
//        }
//            .addOnSuccessListener { Log.d(TAG, "Transaction success!") }
//            .addOnFailureListener { e -> Log.w(TAG, "Transaction failure.", e) }


    }


}