MainTutorialNpc = {
	--To be used for jadespear and ironheart

	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.level >= 5 and player.class == 0 then
			-- choose path -- Peasant lvl 5 check/bother

			local opts = {
				"Bisakah kau menjelaskan jalur-jalurnya?",
				"Tunjukkan guild Warrior.",
				"Tunjukkan guild Rogue.",
				"Tunjukkan guild Mage.",
				"Tunjukkan guild Poet.",
				"Nanti saja aku memilih."
			}

			player:dialogSeq(
				{
					t,
					"Astaga, kau tumbuh cepat! Kau sudah mencapai level 5. Tapi kulihat kau belum memilih jalurmu.",
					"Sebaiknya kau benar-benar memikirkan pilihan jalurmu sebelum melanjutkan tugas-tugas ini."
				},
				1
			)

			local choice = player:menuSeq(
				"Mau kukirim ke guild-mu untuk memilih takdirmu, atau kau ingin melanjutkan dulu?",
				opts,
				{}
			)

			local returnText = "Remember to return to me later, so you can continue your tutorial."

			if choice == 1 then
				player:dialogSeq(
					{
						t,
						"Di tanah ini ada 4 jalur utama: Warrior, Rogue, Mage, dan Poet.",
						"Warrior adalah petarung; mereka memakai kekuatan kasar untuk membunuh musuh dan bisa menerjang banyak mob dengan cepat.",
						"Rogue juga petarung, tetapi lebih banyak dibantu sihir. Pembunuh yang gesit dan mematikan dalam duel.",
						"Mage adalah pengguna sihir di tanah ini. Kuat dalam seni sihir serang dan mengandalkan serangan jarak jauh",
						"Terakhir, Poet. Merekalah penyembuh di tanah ini. Meski sedikit membunuh sendiri, mereka selalu disambut di tiap kelompok.",
						"Kau bisa mempelajari tiap jalur lebih jauh dari tutor Guild, yang ada di sudut kiri bawah tiap guild."
					},
					1
				)
			end

			if choice == 2 then
				-- warriors guild
				player:dialogSeq(
					{
						t,
						"Ah, hati seorang petarung. Inilah jalur bagi petarung sejati. Mari kutunjukkan aulanya sekarang.",
						returnText
					},
					1
				)
				player:warp(341, 8, 7)
			end

			if choice == 3 then
				-- rogues guild
				player:dialogSeq(
					{
						t,
						"Kau ingin jadi petarung yang gesit? Mari kutunjukkan aulanya sekarang.",
						returnText
					},
					1
				)
				player:warp(343, 8, 7)
			end

			if choice == 4 then
				-- mage guild
				player:dialogSeq(
					{
						t,
						"Penguasaan sihir yang kau cari? Mari kutunjukkan aulanya sekarang.",
						returnText
					},
					1
				)
				player:warp(342, 8, 7)
			end

			if choice == 5 then
				-- poet guild
				player:dialogSeq(
					{
						t,
						"Kau memang berjiwa penyayang dan mengasuh. Mari kutunjukkan aulanya sekarang.",
						returnText
					},
					1
				)
				player:warp(344, 8, 7)
			end

			if choice == 6 then
				-- pick later
				player:dialogSeq(
					{
						t,
						"Ini pilihanmu... Tapi ingat, semua pengalaman yang kau peroleh sampai kau memilih jalur akan terbuang percuma. Pilihlah jalurmu segera..."
					},
					1
				)
			end

			return
		end

		if player.baseClass == 1 and player.level >= 5 then
			local chongun = {
				graphic = convertGraphic(29, "monster"),
				color = 12
			}

			if player.quest["tiger_armor"] == 0 then
				player:dialogSeq(
					{
						t,
						"Kau ingin mempelajari sari sang harimau?",
						"Listen carefully."
					},
					1
				)
				player:dialogSeq(
					{
						chongun,
						"Ada jiwa yang sangat tua bersemayam di dalam sebuah gua. Bergegaslah menemuinya."
					},
					1
				)
				player:dialogSeq(
					{
						t,
						"Kalau sempat, masuklah dan berbicaralah kepadanya. Ia akan mengisi zirahmu dengan sari sang harimau.",
						"Ia mengenalku dan mengingat gelar lamanya, Chongun. Temui dia segera. Katakan itu kepadanya, dan berikan apa yang ia minta.",
						"Tapi hati-hati: yang menjadi zirahmu adalah sari di dalam dirimu, yaitu pengalamanmu sendiri.",
						"Pergilah ke jantung gua Harimau dan ucapkan Chongun."
					},
					0
				)
				return
			end

			if player.quest["tiger_armor"] == player.level then
				player:dialogSeq({
					t,
					"Zirah harimaumu tampak ketinggalan zaman. Temui lagi kawanku Claw untuk meningkatkannya."
				})
				return
			end
		end

		if player.quest["tutorial_quest"] == 0 then
			player.quest["tutorial_quest"] = 1
			player:dialogSeq(
				{
					t,
					"Salam, selamat datang di rumahku. Kulihat kau tidak sabar melanjutkan petualanganmu. Tapi sebelum itu, masih banyak yang perlu kau pelajari. Klik aku untuk belajar... "
				},
				0
			)
			return
		end

		if player.quest["tutorial_quest"] == 1 then
			local items = {"war_platemail", "spring_mail_dress"}

			local neededItem = items[player.sex + 1]

			if player:hasItem(neededItem, 1) == true or player:hasEquipped(neededItem.yname) then
				player:giveXP(100)
				player.quest["tutorial_quest1_gave_gold"] = 0
				player.quest["tutorial_quest"] = 2

				player:dialogSeq(
					{
						t,
						"Kerjamu bagus. Simpan zirah itu. Ia akan berguna melawan musuh pertamamu... nanti. Tekan <u> untuk mengenakannya.",
						"Kalau kau mau tugas lagi, bilang saja. Masih banyak yang bisa kuajarkan kepada anak muda sepertimu."
					},
					0
				)
				return
			end

			local titem = {
				graphic = Item(neededItem).icon,
				color = Item(neededItem).iconC
			}
			local tblacksmith = {
				graphic = convertGraphic(6, "monster"),
				color = 13
			}

			if player.quest["tutorial_quest1_gave_gold"] == 1 then
				player:dialogSeq(
					{
						titem,
						"Aku masih menunggu " .. Item(neededItem).name .. " itu. Temuilah pandai besi."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Seorang pahlawan harus sangat memahami dunia, dan itu hanya datang dari pengalaman.",
					"Pengalaman akan mengajarimu bahwa sebaiknya kau melengkapi dirimu dengan baik.",
					"Kau perlu paham cara membeli dan menjual barang. Pertama, kau harus membeli sesuatu..."
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"Kau sudah mendapat sedikit kepercayaan prajurit tua ini. Ini uang untuk membeli zirah."
				},
				1
			)
			player:addGold(20)

			player.quest["tutorial_quest1_gave_gold"] = 1
			player:dialogSeq(
				{
					titem,
					"Pergilah ke pandai besi, ambil " .. Item(neededItem).name .. ", lalu bawa kembali. Kau akan menemukannya di daftar <Peasant's Clothes>"
				},
				1
			)
			player:dialogSeq(
				{
					tblacksmith,
					"Pandai besi ada di Buya pada 18,103, atau di Kugnae pada 60,122. Kalau lupa, periksa peta kecil dengan menekan 'm'."
				},
				1
			)
			player:dialogSeq(
				{
					tblacksmith,
					"Coba saja klik si orang tua itu, pikirannya cuma satu arah. Ia pasti mencoba menjual sesuatu kepadamu."
				},
				1
			)
			player:dialogSeq(
				{
					titem,
					"Ambil " .. Item(neededItem).name .. " sekarang. Kembalilah dan ketuk aku"
				},
				0
			)
			return
		end

		if player.quest["tutorial_quest"] == 2 then
			-- Buying Food

			if (player:hasItem("meat_scrap", 1) == true) then
				player:removeItem("meat_scrap", 1, 9)
				player:giveXP(100)
				player.quest["tutorial_quest"] = 3
				player.quest["tutorial_quest2_gave_meat"] = 0
				player:dialogSeq(
					{
						t,
						"Kau jauh lebih baik daripada murid yang terakhir. Dia... yah, tidak usah kubahas."
					},
					1
				)
				player:dialogSeq(
					{
						t,
						"Pertahankan, dan suatu saat orang mungkin menyebutmu Pahlawan... atau setidaknya pedagang."
					},
					1
				)
				player:dialogSeq(
					{
						t,
						"Kalau kau mau tugas lagi, bilang saja. Masih banyak yang bisa kuajarkan kepada anak muda sepertimu."
					},
					0
				)
				return
			end

			local tbutcher = {
				graphic = convertGraphic(11, "monster"),
				color = 3
			}
			local trabbitmeat = {
				graphic = Item("rabbit_meat").icon,
				color = Item("rabbit_meat").iconC
			}
			local tmeatscraps = {
				graphic = Item("meat_scrap").icon,
				color = Item("meat_scrap").iconC
			}

			if player.quest["tutorial_quest2_gave_meat"] == 1 then
				player:dialogSeq(
					{
						tmeatscraps,
						"Daging kelincinya sudah kuberikan. Belilah meat scrap selagi kau di tukang daging."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Uang tidak membuat seseorang jadi manusia, tetapi ia bisa memperbaiki pedang. Bawalah sebagian daging binatang itu ke tukang daging."
				},
				1
			)
			player:dialogSeq(
				{
					tbutcher,
					"Dia pelit... tetapi itu salah satu cara mendapat uang. Lagi pula, kalau kau ke tukang daging, belajarlah menjual."
				},
				1
			)
			player.quest["tutorial_quest2_gave_meat"] = 1
			player:addItem("rabbit_meat", 5)
			player:dialogSeq(
				{
					trabbitmeat,
					"Ini lima bangkai kelinci. Huh! baunya mulai menyengat. Bawa ke toko tukang daging."
				},
				1
			)
			player:dialogSeq(
				{
					tbutcher,
					"Tukang daging ada di Buya pada 39,129, atau di Kugnae pada 41,131. Kalau lupa, periksa peta kecil dengan menekan 'm'."
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"Kau akan lihat tawaran macam apa yang ia berikan padamu. Dan jangan mampir ke mana-mana saat kembali."
				},
				1
			)
			player:dialogSeq(
				{
					tmeatscraps,
					"Beli satu Meat scrap selagi kau di tukang daging. Aku menunggu Meat scrap itu."
				},
				0
			)
			return
		end

		if player.quest["tutorial_quest"] == 3 then
			-- Finding Items

			if (player:hasItem("chestnut", 5) == true and player:hasItem("rose", 1) == true) then
				player:removeItem("chestnut", 5, 9)
				player:removeItem("rose", 1, 9)
				player:giveXP(150)
				player.quest["tutorial_quest"] = 4

				player:dialogSeq(
					{
						t,
						"Sempurna! Rose untuk kekasihku, dan Chestnut untuk dimakan. Terima kasih sudah mengambilkannya.",
						"Ingat, ada cara lain mendapatkan barang untuk dijual kepada pedagang atau warga lain."
					},
					1
				)

				player:dialogSeq(
					{
						t,
						"Kalau kau mau tugas lagi, bilang saja. Masih banyak yang bisa kuajarkan kepada anak muda sepertimu."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Sekarang kau lumayan pandai berdagang, tetapi kau baru tahu cara membeli dan menjual barang yang sudah kau punya.",
					"Kau perlu belajar mendapatkan barangmu sendiri. Memang sebagian datang dari makhluk yang kau bunuh, tetapi masih banyak yang bisa kau temukan."
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"Pertama, carikan aku sekuntum Rose. Kudengar ada semak di kota tempat kau bisa memetiknya."
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"Di Buya semaknya ada di Selatan, sekitar 112,138. Di Kugnae, semaknya dekat Lotus Chapel pada 152,190.\n\nDekati saja semaknya dan kau akan menemukannya.",
					"Aku juga ingin Chestnut; kumpulkan 5 untukku di Barat Laut Buya pada 27,47. Di Kugnae ada ladang kecil pada 111,156.\n\nBentuknya kacang gelap kecil, jadi carilah dengan teliti.",
					"Kumpulkan barang-barang itu, dan temui aku lagi kalau sudah kau dapat."
				},
				0
			)
			return
		end

		if player.quest["tutorial_quest"] == 4 then
			-- Fishing

			if (player:hasItem("minnow", 1) == true and player.quest["learned_to_fish"] == 1) then
				player:removeItem("minnow", 1, 9)
				player:giveXP(50)
				player:addGold(5)
				player.quest["tutorial_quest"] = 5
				player:dialogSeq(
					{
						t,
						"Terima kasih ikannya! Tidak sesulit itu, kan?",
						"Aku dengar cerita tentang orang yang menemukan hal-hal aneh saat memancing.",
						"Kalau kau mau tugas lagi, bilang saja. Masih banyak yang bisa kuajarkan kepada anak muda sepertimu."
					},
					1
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Jadi kau mau kegiatan lain? Memancing bisa melemahkan keahlian bertarungmu, tetapi sesekali menyenangkan sebagai selingan.",
					"Temui Bate di sisi barat Kugnae pada 28,170 atau Wim di tenggara Buya pada 109,88. Katakan lantang padanya 'aku ingin memancing'.",
					"Kalau kau membawakan satu Minnow, akan kuberi sedikit emas."
				},
				0
			)
			return
		end

		if player.quest["tutorial_quest"] == 5 then
			-- Exploration
			if player.quest["talked_to_tutor"] == 1 then
				player:giveXP(150)
				player.quest["tutorial_quest"] = 6
				player.quest["talked_to_tutor"] = 0

				-- frees this variable so we can reduce player registries

				player:dialogSeq(
					{
						t,
						"Aku senang kau sudah menemukan jantung kerajaan kami, dan semoga kau menikmati berkeliling di dalam tembok kerajaan yang aman.",
						"Kalau kau mau tugas lagi, bilang saja. Masih banyak yang bisa kuajarkan kepada anak muda sepertimu."
					},
					1
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Nah, kita mulai tahu jalan sekarang, bukan? Tapi apakah kau memahami Kerajaan di sekelilingmu?",
					"Sudah saatnya kau menyerap sedikit budaya.\n\nPergilah ke istana dan lihat-lihatlah. Istana utama beserta penyambut kerajaan ada di Buya pada 73,56, atau di Kugnae pada 110,123.",
					"Di tenggara Buya terletak Kerajaan Koguryo, yang ibu kotanya Kugnae. Rajanya bernama Mhul.",
					"Kau juga akan menemukan teater tempat para Muse sesekali mengadakan sayembara puisi, dan perpustakaan tempat pengetahuan kerajaan kami disimpan.",
					"Setibanya di perpustakaan, sebutkan namaku kepada pustakawan; aku yakin ia punya sesuatu untuk dikatakan kepadamu. Ingat, ucapkan namaku dengan lantang."
				},
				0
			)
			return
		end

		if player.quest["tutorial_quest"] == 6 then
			-- Ogre Cider

			if (player:hasItemDura("ogre_cider", 1) == true) then
				player:giveXP(150)
				player.quest["tutorial_quest"] = 7
				player:removeItem("ogre_cider", 1, 9)

				player:dialogSeq(
					{
						t,
						"Hebat, kau membawa Ogre cider! Tidak ada yang menandingi cider untuk menyudahi santapan.",
						"Kalau kau ingin berhasil, kau harus menjelajahi banyak tempat di luar kota yang nyaman.",
						"Kalau kau mau tugas lagi, bilang saja. Masih banyak yang bisa kuajarkan kepada anak muda sepertimu."
					},
					1
				)
				return
			end

			local item = Item("ogre_cider")
			local togrecider = {graphic = item.icon, color = item.iconC}
			local togre = {graphic = convertGraphic(185, "monster"), color = 14}

			player:dialogSeq(
				{
					t,
					"Dunia jauh lebih luas daripada kota. Tanah lain punya bentang alam, tantangan, dan barang yang berbeda."
				},
				1
			)

			player:dialogSeq(
				{
					togrecider,
					"Aku ingin sekali ogre cider, tetapi barang itu sulit dicari di sini. Kebanyakan ogre juga akan menghajarmu habis-habisan."
				},
				1
			)

			player:dialogSeq(
				{
					togre,
					"Tapi kudengar di Hamgyong Nam-Do di tenggara tinggal seekor ogre yang cukup ramah dan kadang berdagang dengan manusia."
				},
				1
			)

			player:dialogSeq(
				{
					t,
					"Untuk pergi ke tanah lain, keluarlah dulu lewat gerbang utara kota. Kau akan sampai di tempat berkumpul, dan dari sana kau bisa naik ke sebuah peta.",
					"Kunjungi Hamgyong Nam-Do dan ambilkan cider untukku."
				},
				1
			)

			player:dialogSeq(
				{
					togrecider,
					"Aku menunggu cider itu di sini. Jangan sampai tersesat!"
				},
				0
			)
			return
		end

		if player.quest["tutorial_quest"] == 7 then
			-- By the Sea (Chu Rua)

			if (player:hasLegend("aided_chu_rua")) then
				player.quest["tutorial_quest"] = 8
				player:dialogSeq(
					{t, "Dragon King akan membaik berkat dirimu."},
					1
				)
				return
			end

			local tturtle = {
				graphic = convertGraphic(174, "monster"),
				color = 0
			}

			player:dialogSeq(
				{
					t,
					"Dragon King tinggal di bawah ombak, tetapi ia jatuh sakit parah. Aku tahu karena seekor kura-kura memberitahuku."
				},
				1
			)
			player:dialogSeq(
				{
					tturtle,
					"Kura-kura itu, bernama Chu Rua, berenang ke pantai untuk meminta pemuda daratan menolongnya. Ia sedang susah dan butuh bantuan."
				},
				1
			)

			player.npcGraphic = t.graphic
			player.npcColor = t.color

			local cchoice = player:menuSeq(
				"Maukah kau menemuinya sekarang? Hati-hati, perjalanannya berbahaya dan kau bisa tersesat.",
				{"Aku bersedia mengambil risikonya", "Oh, kalau begitu, lupakan saja"},
				{}
			)

			if (cchoice == 1) then
				-- willing to risk...
				player:dialogSeq(
					{
						t,
						"Aku tahu jalan rahasia ke pantai. Dari sana kau harus menemukan Chu Rua dan memakai seluruh akal serta kelicikanmu agar berhasil."
					},
					1
				)
				player:warp(1111, 4, 18)
			elseif (cchoice == 2) then
				--nevermind
				player:dialogSeq({t, "Biarlah begitu."}, 0)
			end
			return
		end

		if player.quest["tutorial_quest"] == 8 then
			-- group hunting

			local tdeer = {graphic = convertGraphic(89, "monster"), color = 5}
			if player:hasItem("antler", 3) == true then
				player:removeItem("antler", 3, 9)
				player:giveXP(200)
				player.quest["tutorial_quest"] = 9

				sanctuary.cast(npc, player)

				player:dialogSeq(
					{
						t,
						"Kau petarung hebat yang belajar dengan baik. Semoga kau bertarung dengan baik dan melindungi anggota lainmu.",
						"Kalau kau mau tugas lagi, bilang saja. Masih banyak yang bisa kuajarkan kepada anak muda sepertimu."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Kau berkembang baik sendirian, tetapi legenda yang menyendiri cepat mati. Jalinlah ikatan dengan orang lain untuk petualangan besar.",
					"Tutup gulungan ini. Tekan <f> atau klik tab 'GROUP'. Lalu kembali dan lanjutkan membaca.",
					"Itulah status grupmu, yang mungkin masih kosong sekarang.",
					"Kau memperoleh lebih banyak Pengalaman saat berada dalam grup. Kau dan para anggota mendapat total Pengalaman yang lebih besar.",
					"Carilah orang yang levelnya sepadan denganmu, supaya kalian berdua mendapat cukup Pengalaman.",
					"Bertualanglah dengan orang yang kau sukai dan percayai. Kau akan tahu Jalur mana yang saling melengkapi. Poet selalu membantu kelompok.",
					"Tekan <shift><g> supaya kau bisa diajak bergabung ke dalam grup.",
					"Kau bisa berbisik kepada seseorang dengan menekan \"<shift><quote> lalu mengetik namanya.",
					"Dalam grup, kau bisa berbisik kepada semua anggota dengan menekan \"<shift><quote> lalu mengetik !<shift><1> dua kali."
				},
				1
			)

			player:dialogSeq(
				{
					tdeer,
					"Pergilah sekarang, bergrup dengan beberapa orang untuk memburu sekitar 12 rusa (yang bertanduk). Buru lebih banyak kalau grupmu lebih besar.",
					"Sebaiknya tanduknya dibagi adil. Omong-omong, tanduk itu sangat berguna bagi prajurit karena menyimpan kekuatan sang rusa.",
					"Kalau ditumbuk dan dimakan <u>, daya hidupnya mengalir ke dalam dirimu. Hati-hati sekali. Kalau kau sehijau penampilanmu, kau tidak akan selamat melawan seekor rusa!"
				},
				0
			)
			return
		end

		if player.quest["tutorial_quest"] == 9 then
			-- Spelunking

			local tmica = {
				graphic = Item("mica").icon,
				color = Item("mica").iconC
			}
			local trats = {graphic = convertGraphic(90, "monster"), color = 11}
			local tpotion = {
				graphic = Item("blue_potion").icon,
				color = Item("blue_potion").iconC
			}

			if player:hasItem("mica", 1) == true then
				player:removeItem("mica", 1, 9)
				player.quest["tutorial_quest"] = 10
				player:addItem("blue_potion", 1)
				player:giveXP(500)

				player:dialogSeq({tmica, "Mica! Persis yang kubutuhkan."}, 1)
				player:dialogSeq(
					{
						tpotion,
						"Ambil ini, salah satu ramuan buatanku. Ia akan menyembuhkan sebagian lukamu..."
					},
					1
				)
				player:dialogSeq(
					{
						t,
						"dan ingatlah, kau akan menemukan rahasia yang jauh lebih besar di gua-gua lain.",
						"Kalau kau mau tugas lagi, bilang saja. Masih banyak yang bisa kuajarkan kepada anak muda sepertimu."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Sekarang kau siap untuk sesuatu yang sedikit lebih menantang!\nSelama ini perjalananmu terbatas di tanah permukaan, tempat Matahari menjauhkan banyak kejahatan.",
					"Tetapi ganjaran - dan tantangan - yang jauh lebih besar menantimu di bawah tanah."
				},
				1
			)
			local choice = player:menuSeq(
				"Aku sedang meramu obat penyembuh. Maukah kau membantuku mengumpulkan bahannya?",
				{"Ya. Aku siap untuk hal yang lebih besar.", "Tidak, tidak sekarang."},
				{}
			)

			if choice == 1 then
				player:dialogSeq(
					{
						t,
						"Bagus. Semua bahannya sudah ada kecuali mica."
					},
					1
				)
				player:dialogSeq(
					{
						trats,
						"Ada gua, tidak jauh dari sini, tempat tikus putih kadang bisa ditemukan."
					},
					1
				)
				player:dialogSeq(
					{
						t,
						"Sarangnya ada dekat sebuah sumur, di bawah pohon emas tinggi dekat Dusk Shaman."
					},
					1
				)
				player:dialogSeq(
					{
						trats,
						"Tikus-tikus itu dirusak oleh chi jahat. Mereka tidak hidup dari makanan seperti kita, melainkan dari memakan batu yang menjadi rumahnya."
					},
					1
				)
				player:dialogSeq(
					{
						tmica,
						"Kadang kau akan mendapati mereka membawa mica, mineral yang ada di dalam batuan daerah ini."
					},
					1
				)
				player:dialogSeq(
					{
						t,
						"Hati-hati! Banyak makhluk yang hidup di bawah tanah jauh lebih berbahaya daripada yang pernah kau temui selama ini.",
						"Bawakan aku sekeping mica supaya aku bisa membuat ramuanku."
					},
					0
				)
			elseif choice == 2 then
				player:dialogSeq({t, "Kalau begitu, mungkin lain kali."}, 0)
			end

			return
		end

		if player.quest["tutorial_quest"] == 10 then
			-- Horse Riding
			local thorse = {graphic = convertGraphic(17, "monster"), color = 3}

			if (player.state == 3 and player.disguise == 26) then
				-- mounted and horse graphic
				player:giveXP(500)
				player.quest["tutorial_quest"] = 11

				player:dialogSeq(
					{
						t,
						"Tunggangan yang hebat. Sungguh mengesankan; aku suka memperhatikan kuda.",
						"Kalau kau mau tugas lagi, bilang saja. Masih banyak yang bisa kuajarkan kepada anak muda sepertimu."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Tidak ada yang lebih baik daripada tunggangan cepat yang membawamu ke tujuanmu. Belajarlah menunggang sekarang, itu akan sangat membantumu menuju takdirmu."
				},
				1
			)
			player:dialogSeq(
				{
					thorse,
					"Kau tidak bisa berbuat banyak di atas kuda, tetapi jauh lebih cepat daripada berjalan. Carilah kuda dan tunggangilah kembali ke sini."
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"Bicaralah lagi kepadaku saat kau menunggang kuda. Bagian kiri atas kota tempat yang bagus untuk mencari kuda; biasanya ada beberapa di sana. Begitu ketemu, dekati dan tunggangi dengan menekan tombol [r]."
				},
				0
			)
			return
		end

		if player.quest["tutorial_quest"] == 11 then
			if player.quest["helped_haguru"] == 1 then
				player:giveXP(500)
				player.quest["helped_haguru"] = 0
				player.quest["tutorial_quest"] = 12

				player:dialogSeq(
					{
						t,
						"Oh, terima kasih banyak sudah menemukan adikku! Beban pikiranku jadi hilang. Ia sungguh mulia, mau menolong kota itu seperti tadi.",
						"Kalau kau mau tugas lagi, bilang saja. Masih banyak yang bisa kuajarkan kepada anak muda sepertimu."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Astaga... aku baru saja menerima kabar buruk. Adik bungsuku hilang dari rumahnya. Bersediakah kau menolongku?"
				},
				1
			)

			local choice = player:menuSeq(
				"Maukah kau mencarinya dan mencari tahu apa yang terjadi?",
				{"Tidak, aku tidak mau menolong.", "Ya, aku akan mencarinya."},
				{}
			)

			if choice == 1 then
				player:dialogSeq(
					{
						t,
						"Oh, sayang sekali. Maaf, tetapi aku tidak bisa melanjutkan latihanmu sampai aku tahu apa yang menimpanya"
					},
					0
				)
			elseif choice == 2 then
				player:dialogSeq(
					{
						t,
						"Yang bisa kukatakan hanyalah bahwa ia tinggal di Sanhae, kota jauh di utara.",
						"Kau bisa ke sana dengan kembali ke kandang kuda lalu bepergian ke Arctic Land.",
						"Dari sana pergilah ke tenggara dan susuri lembahnya. Bicaralah dengan Wali Kota di sana, mungkin ia bisa menceritakan apa yang terjadi."
					},
					0
				)
			end
			return
		end

		if player.quest["tutorial_quest"] == 12 then
			-- Better WEapon

			if player:hasLegend("defeated_ice_beast") then
				player.quest["tutorial_quest"] = 13
				player:dialogSeq(
					{
						t,
						"Bagus, kulihat kau sudah beralih ke senjata yang lebih baik. Semoga cocok untukmu.",
						"Kalau kau mau tugas lagi, bilang saja. Masih banyak yang bisa kuajarkan kepada anak muda sepertimu."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Jadi kau masih membawa-bawa tongkat kecil itu untuk memukul? Kurasa kau perlu beralih ke sesuatu yang lebih baik!",
					"Coba kupikir... Ah ya! Senjata bagus dengan sifat sihir yang hebat, dan mungkin tantangan yang terlalu besar bahkan untukmu.",
					"Aku pernah dengar kisah tentang seorang prajurit yang agak licik di perkemahan KaMing. Kau bisa ke sana dari kandang kuda yang tadi kau pakai.",
					"Pergilah ke sana dan bicarakan soal senjata baru. Aku yakin ia bersedia \"membantu\"mu; tanyakan tentang \"Ice beast\""
				},
				0
			)
			return
		end

		if player.quest["tutorial_quest"] == 13 then
			-- End of tutorial quest

			local tstudentcap = {
				graphic = Item("student_cap").icon,
				color = Item("student_cap").iconC
			}

			if player:hasItem("student_cap", 1, 0, player.ID) == true or player:hasEquipped("student_cap") == true then
				-- checks if player has student cap and that it also belongs to them
				player:giveXP(2000)
				player.quest["tutorial_quest"] = 14
				player.quest["visited_yon_and_weaved"] = 0
				player:dialogSeq(
					{
						t,
						"Semua yang bisa kuajarkan sudah kuajarkan, anak muda. Kini saatnya kau melangkah ke Kerajaan-kerajaan dan menciptakan legendamu sendiri."
					},
					0
				)
				return
			end

			if player.quest["visited_yon_and_weaved"] == 1 and player:hasItem("cloth", 1) == true then
				-- checks if playeer actually made the cloth through the npc and that the player still has at least 1 cloth in possession
				player:dialogSeq(
					{
						t,
						"Ah, kulihat kau sudah menemui Yon.. bagaimana dia?",
						"Setelah kainnya kau punya, temuilah Caretaker museum yang tinggal di museum utara Dae Shore. Hanya dia yang bisa membuat Student Cap-mu."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Nah, waktumu bersamaku hampir usai, tetapi sebelum berpisah aku ingin memberimu hadiah kecil sebagai kenangan."
				},
				1
			)
			player:dialogSeq(
				{
					tstudentcap,
					"Akan kutunjukkan cara membuktikan kelayakanmu: membuat Student's cap-mu sendiri!"
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"Benda ini memberi perlindungan yang lumayan dalam pertempuran meski terbuat dari kain.",
					"Langkah pertama membuat topimu adalah mendapatkan kain.\nPergilah ke tengah belantara, di sana ada domba-domba.",
					"Kumpulkan wol yang mereka jatuhkan lalu bawa ke pondok penenun. Ada banyak keahlian yang bisa kau pelajari nanti; menenun hanya salah satunya.",
					"Ada pembuatan zirah, penempaan senjata dari kayu maupun logam, pengasahan permata, bahkan memasak!",
					"Penenunnya ada sekitar 45,30 di Wilderness, di luar gerbang Utara Kugnae. Tanyakan soal \"tenun\" setibanya di sana.",
					"Jalannya cukup jauh, jadi sebaiknya pakai kuda. Pergilah sekarang, dan kembalilah kalau kainnya sudah jadi."
				},
				0
			)
			return
		end

		if player.quest["tutorial_quest"] == 14 then
			player:dialogSeq(
				{
					t,
					"Semua yang bisa kuajarkan sudah kuajarkan, anak muda. Saatnya kau melangkah ke Kerajaan-kerajaan dan menciptakan legendamu sendiri."
				},
				0
			)
			return
		end
	end),

	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)

		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if speech == "bintang" then
			Tools.checkKarma(player)

			if player.level < 60 then
				return
			end

			player:dialogSeq(
				{
					t,
					"Kau ingin memahami bintang? Caranya sederhana. Kunjungi pusat bintang berujung dua belas. Jatuhkan satu white amber di sana. Maka kau akan mengerti."
				},
				0
			)
			return
		end
	end)
}
