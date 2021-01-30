package com.example.makeyourcs.data.firebase

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import android.net.Uri

import androidx.lifecycle.MutableLiveData
import com.example.makeyourcs.data.AccountClass
import com.example.makeyourcs.data.PostClass
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import io.reactivex.Completable
import java.lang.Boolean.FALSE
import java.time.LocalDateTime
import kotlinx.android.synthetic.main.activity_storage.*
import java.text.SimpleDateFormat
import java.util.*

class   FirebaseAuthSource {
    val TAG = "FirebaseSource"
    val userDataLiveData = MutableLiveData<AccountClass>()
    val accountDataLiveData = MutableLiveData<List<AccountClass.SubClass>>()
    val followerWaitlistLiveData = MutableLiveData<List<AccountClass.Follower_wait_list>>()
    val postDataLiveData = MutableLiveData<PostClass>()

    private val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }
    private val firestore: FirebaseFirestore by lazy{
        FirebaseFirestore.getInstance()
    }
    private val firebaseStorage: FirebaseStorage by lazy{
        FirebaseStorage.getInstance()
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

    //TODO:게시글 파트
    fun uploadPhoto(photoUri: Uri) {
        var timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        var fileName = "IMAGE_" + timestamp + "_.png"//photoUri 받아서 뷰 모델에서 이름 설정
        //images를 폴더명으로 하고 있으나 업로드 유저 아이디를 폴더명으로 할 예정
        var storageRef = firebaseStorage.reference.child("images/"+fileName)
//        var tmpid = 1;
//        var firestore = firestore.collection("Post")?.document(tmpid.toString())?.update(
//            mapOf(
//                "picture_url" to storageRef.toString()
//            )
//        );
        //모델에서 다운로드
        storageRef.putFile(photoUri).addOnSuccessListener {
            Log.d(TAG, "Upload photo completed")
        }
    }

    fun setPhoto() {
        var posting = PostClass()
        posting.postId = 1 //난수로 시스템에서 아이디생성
        posting.post_account = "sobinsobin"
        posting.content = "life without fxxx coding^^"
        posting.first_pic = "../images/test.jpg"
        posting.place_tag = "homesweethome"
        try{
            firestore?.collection("Post")?.document(posting.postId.toString())?.set(posting)
        }
        catch (e: Exception){
            Log.d("cannot upload", e.toString())
        }

    }


    fun observePostData() {
        System.out.println("observePostData")
        System.out.println("observePostData2: " + currentUser()!!.email)

        try {
            firestore.collection("Post").whereEqualTo("post_account", currentUser()!!.email.toString()).addSnapshotListener{ value, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e)
                    return@addSnapshotListener
                }
                for (doc in value!!) {
                    doc?.let {
                        val data = it?.toObject(PostClass::class.java)
                        System.out.println(data)
                        postDataLiveData.postValue(data)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user data", e)
        }
    }

    fun deletePhoto(){ //추후 delete하는 Activity에 추가
//        FirebaseStorage.getInstance().reference.child("images").child(delete_filename_edittext.text.toString()).delete()

    }


    fun setPost()
    {
        var posting = PostClass()
        posting.postId = 1//난수로 시스템에서 아이디생성
        posting.post_account = "dmlfid1348"
        posting.content = "희루가기시러"
        //posting.first_pic = "../images/test.jpg"
        posting.place_tag = "huiru"
        try{
            firestore?.collection("Post")?.document(posting.postId.toString())?.set(posting)
        }
        catch(e: java.lang.Exception){
            Log.d("cannot upload", e.toString())
        }

    }
    fun getPost(postId:Int)
    {
        try{
            firestore?.collection("Post")?.document(postId.toString())?.get()?.addOnCompleteListener{task->
                if(task.isSuccessful){
                    val posting = PostClass()
                    posting.postId = task.result!!["postId"].toString().toInt()
                    posting.post_account = task.result!!["post_account"].toString()
                    posting.content = task.result!!["content"].toString()
                    posting.first_pic = task.result!!["first_pic"].toString()

                    System.out.println(posting)
                }

            }
        }catch(e: java.lang.Exception)
        {
            Log.d("cannot get", e.toString())
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