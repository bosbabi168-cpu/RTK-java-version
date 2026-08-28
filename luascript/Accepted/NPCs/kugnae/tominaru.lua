TominaruNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {
			"Lesson 1 'Karma'",
			"Lesson 2 'Grouping'",
			"Lesson 3 'Legend'",
			"Lesson 4 'Emotions'",
			"Lesson 5 'Knowing yourself'"
		}

		local choice = player:menuSeq(
			"Halo! Ada yang bisa kubantu hari ini?",
			opts,
			{}
		)

		if choice == 1 then
			player:dialogSeq(
				{
					t,
					"Selamat datang. Aku senang melihat jiwa mudamu tumbuh. Di sini banyak yang bisa mengajarkanmu cara hidup yang biasa. Aku bisa mengajarkanmu jalan hati nurani, atau Karma-mu.",
					"Semua yang kau pikirkan dan lakukan bisa memengaruhi Karma-mu. Kau bisa memilih berbuat baik atau berbuat jahat. Keduanya memengaruhi nuranimu.",
					"Ada banyak cara menambah atau mengurangi Karma-mu. Entah kau memilih merangkak seperti yang terkutuk di Bumi atau menyatu dengan para Dewa, ada banyak cara mengetahui tingkat Karma-mu saat ini.",
					"Para Monk, Geomancer, dan Diviner bisa menjangkau jiwamu. Mereka bisa memberitahumu apakah Karma-mu Snake atau Shunnyo ((malaikat)). Bahkan pedagang pun enggan berbicara dengan pemilik Karma Snake",
					"Kalau kau berjuang keras dengan keberanian besar, Karma-mu melambung. Kalau kau lari dari tantangan atau melanggar hukum, Karma-mu terjun bebas.",
					"Kau bahkan bisa membuat orang lain memperoleh Karma. Banyak orang di negeri ini berusaha menolong yang muda seperti",
					"Sesama warga yang lebih berpengalaman bisa membimbingmu dengan menaikkan tingkat keahlian dan pengetahuanmu.",
					"Mereka yang mengejar Karma sejati tidak melakukannya demi uang, melainkan karena hasrat menolong. Ketamakan adalah contoh sikap yang menghalangi bertambahnya Karma.",
					"Jagalah jiwamu dengan tidak membiarkan emas atau barang dibayarkan kepada atau oleh Pembimbing. Ketamakan tidak ada hubungannya dengan pencerahan jiwa yang sejati.",
					"Seiring kebijaksanaan dan pengetahuanmu bertambah, karmamu bisa berada di banyak tingkat. Bersekutu dengan salah satu makhluk Mythic adalah satu cara yang bisa mendatangkan banyak Karma.",
					"Menyalahgunakan hukum umum diketahui menurunkan Karma, tetapi banyak yang tidak tahu bahwa mengingkari janji, misalnya bergabung dengan subjalur lalu meninggalkannya, juga memengaruhi Karma-mu.",
					"Belajarlah dari orang lain: sesama warga, Pembimbing, maupun Tutor, lalu pilihlah jalan yang bijak. Jiwa yang baik akan membawamu jauh di jalan hidupmu.",
					"Semoga para Dewa memberkati hidupmu."
				},
				1
			)
			if (player.registry["tominaru1exp"] == 0) then
				player:giveXP(10)
				player.registry["tominaru1exp"] = 1
			end
		elseif choice == 2 then
			player:dialogSeq(
				{
					t,
					"Hidup adalah petualangan besar! Kau tidak harus menghadapinya sendirian! Mungkin awalnya kau baik-baik saja sendiri, tetapi lama-kelamaan hidup jadi terlalu membosankan atau musuhnya terlalu tangguh untuk kau kalahkan seorang diri.",
					"Kau bertanya apa jalan keluarnya? Mudah saja: carilah orang lain untuk bergrup denganmu. ((Tekan shift dan huruf g untuk menyalakan grupmu.))",
					"Bagaimana caranya bergrup? Itu bagian yang mudah. Cara yang paling umum adalah menepuk bahu orang yang ingin kau ajak. ((Klik kiri orang itu lalu klik kotak yang bentuknya seperti dua huruf P saling berhadapan.",
					"Kalau kotak itu tidak menyala, berarti grupnya belum dinyalakan. Kau harus memintanya menyalakannya dulu supaya bisa bergrup.",
					"Kau bisa bergrup dengan sampai sembilan pemain lain! Jadi satu grup bisa terdiri dari sepuluh pemain, denganmu sebagai yang kesepuluh.",
					"((Cara lain adalah menekan g pada papan ketik lalu mengetik nama orang yang ingin kau ajak. Sekali lagi, kalau grupnya belum menyala, kau harus memintanya menyalakannya.))",
					"Saat pemain bergrup, tiap orang mendapat sebagian kecil pengalaman dari monster yang dibunuh. Meski pengalamannya terbagi, bergrup membuatmu membunuh monster lebih cepat, sehingga pengalaman justru terkumpul lebih cepat pula.",
					"Oh! Hampir lupa! Tentu saja membentuk grup lebih mudah kalau kau punya kawan. Catat kawan-kawanmu dalam daftar. ((Tekan F3 pada papan ketik, atau klik menu lalu friends untuk membuka daftarnya.))",
					"Nama kawanmu akan tampil biru di daftar pahlawan ((Tekan ctrl w atau klik menu userlist untuk melihat siapa yang sedang daring.))",
					"Selamat bergrup dan semoga berhasil!"
				},
				1
			)
			if (player.registry["tominaru2exp"] == 0) then
				player:giveXP(10)
				player.registry["tominaru2exp"] = 1
			end
		elseif choice == 3 then
			player:dialogSeq(
				{
					t,
					"Kau masih terlalu muda sehingga legendamu hampir belum ada! Apa itu legenda? Ia cermin dari waktumu di Kerajaan-kerajaan ini, mencatat segala yang kau capai, baik maupun buruk.",
					"((Untuk melihat legendamu, klik tab status atau tekan s pada papan ketik untuk membuka menu status. Lalu klik panah ke kanan di sudut bawah dua kali.))",
					"((Untuk melihat legenda orang lain, klik pemainnya lalu klik panah ke kanan tiga kali.))",
					"Ada banyak cara mengumpulkan tanda pada legendamu: menuntaskan tugas, diakui anggota masyarakatmu, menyaksikan peristiwa besar, atau memegang jabatan seperti hakim, penyelenggara permainan, atau pemandu subjalur. Semua itu menambah satu takik baru pada legendamu.",
					"Tapi hati-hati! Ada juga tanda legenda merah yang memberitahu masyarakat tentang perbuatan burukmu. Tanda itu didapat karena dipenjara, tetapi bisa juga diberikan subjalur atas perbuatan tidak hormat terhadap kelompok mereka.",
					"Makin banyak tanda merah yang kau kumpulkan, makin sedikit hak yang kau miliki di Kerajaan-kerajaan ini, dan terlalu banyak bisa berujung pada pengusiranmu!",
					"Banyak klan dan subjalur menolak menerima pemain yang legendanya bercacat tanda merah.",
					"Ingatlah untuk tetap berada di sisi hukum yang benar agar legendamu rapi dan bersih. Aku tidak ingin melihat orang sepotensial dirimu kehilangan itu karena perbuatan salah."
				},
				1
			)
			if (player.registry["tominaru3exp"] == 0) then
				player:giveXP(20)
				player.registry["tominaru3exp"] = 1
			end
		elseif choice == 4 then
			player:dialogSeq(
				{
					t,
					"Kenapa kau menatapku dengan wajah kosong begitu? Sepertinya kau bahkan lupa cara mengungkapkan perasaanmu.",
					"Saat berhubungan dengan orang di sekitarmu, kau bisa berkomunikasi bukan hanya lewat kata, tetapi juga lewat air muka. Cara yang bagus untuk menunjukkan perasaanmu!",
					"((Untuk membuat ekspresi, tekan : atau shift ; lalu pilih huruf a sampai p. Tiap huruf menghasilkan ekspresi yang berbeda.",
					"((Ini daftar tiap huruf dan ekspresi yang akan dilakukan karaktermu.",
					"a - Senang",
					"b - Sad",
					"c - Embarrased",
					"d - Wink",
					"e - Bored",
					"f - Tired",
					"g - Surprised",
					"h - Angry",
					"i - Sinis",
					"j - Shrug",
					"k - Annoyed",
					"l - Dance",
					"m - Bow",
					"n - Victory",
					"o - Bizarre",
					"p - Kiss"
				},
				1
			)
			if (player.registry["tominaru4exp"] == 0) then
				player:giveXP(25)
				player.registry["tominaru4exp"] = 1
			end
		elseif choice == 5 then
			player:dialogSeq(
				{
					t,
					"Penting untuk selalu menyadari keadaan jasmanimu agar kesehatanmu terjaga dan kau bisa melawan musuh dengan lebih efisien.",
					"((Untuk melihat statusmu, klik tab 'Status', atau tekan 's' pada papan ketik.))",
					"Vitality adalah seberapa besar tenagamu. Makin besar vitality-mu, makin lama waktu yang dibutuhkan untuk membunuhmu bila kau diserang.",
					"Mana adalah seberapa besar kecerdasan yang kau peroleh. Makin besar mana-mu, makin banyak sihir dan kemampuan khusus yang bisa kau lakukan.",
					"Sebagian barang yang kau kenakan menambah peluangmu mengenai musuh, sebagian lain menambah kerusakan yang kau timbulkan dengan senjata jarak dekat.",
					"Kedua hal itu paling perlu diperhatikan oleh Rogue dan Warrior dibanding jalur mana pun.",
					"Selain peluang mengenai sasaran dan besarnya kerusakan, might dan grace adalah dua hal lain yang perlu diingat kelas jarak dekat.",
					"Makin besar might-mu, makin kuat senjata yang bisa kau bawa, dan makin besar pula kerusakan yang kau timbulkan pada musuh.",
					"Makin besar grace-mu, makin lincah kakimu, sehingga kau lebih mudah mengelak dari serangan jarak dekat.",
					"Kekuatan will diperlukan oleh perapal mantra seperti Mage dan Poet.",
					"Makin kuat will-mu, makin kecil kemungkinan mantra dan jampimu gagal terhadap lawan yang kau hadapi.",
					"Seluruh zirah dan perlengkapan yang kau kenakan memengaruhi seberapa berat pukulan yang kau terima. Itu disebut armor class.",
					"Makin rendah armor class-mu, makin ringan kerusakan yang kau terima. ((Ini biasa disebut AC-mu.))"
				},
				1
			)
			if (player.registry["tominaru5exp"] == 0) then
				player:giveXP(25)
				player.registry["tominaru5exp"] = 1
			end
		end
	end)
}
