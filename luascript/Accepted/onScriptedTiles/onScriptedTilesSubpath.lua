onScriptedTilesSubpath = function(player)
	-- wilderness
	if player.m == 1002 then
		-- ranger
		if (player.x == 52 or player.x == 53) and player.y == 32 and (os.time() > player.registry["rangerBan"] + (60 * 8)) then
			local x = math.random(25, 27)
			player:warp(3619, x, 28)
		end

		-- druid
		if (player.x == 172 or player.x == 173) and player.y == 212 and (os.time() > player.registry["druidBan"] + (60 * 8)) then
			local x = math.random(9, 10)
			player:warp(3632, x, 33)
		end
	end

	-- oh mudum crypt
	if player.m == 2205 then
		-- spy
		if (player.x == 10) and player.y == 17 and (os.time() > player.registry["spyBan"] + (60 * 8)) then
			local x = math.random(8, 9)
			player:warp(3519, x, 28)
		end
	end

	-- kugnae
	if player.m == 0 then
		-- diviner
		if (player.x == 126 or player.x == 127) and player.y == 168 and (os.time() > player.registry["divinerBan"] + (60 * 8)) then
			local x = math.random(15, 16)
			player:warp(3540, x, 28)
		end

		-- merchant
		if (player.x == 100) and player.y == 159 and (os.time() > player.registry["merchantBan"] + (60 * 8)) then
			local x = math.random(18, 20)
			player:warp(3524, x, 28)
		end
	end

	-- dae shore
	if player.m == 1004 then
		-- chongun
		if (player.x == 58 or player.x == 59) and player.y == 5 and (os.time() > player.registry["chongunBan"] + (60 * 8)) then
			local x = math.random(25, 26)
			player:warp(3614, x, 33)
		end

		-- monk
		if (player.x == 39 or player.y == 40) and player.y == 7 and (os.time() > player.registry["monkBan"] + (60 * 8)) then
			player:warp(3529, 6, 28)
		end
	end

	-- sanhae hall
	if player.m == 1127 then
		-- do
		if (player.x == 9 or player.x == 10) and player.y == 2 and (os.time() > player.registry["doBan"] + (60 * 8)) then
			player:warp(3609, 12, 28)
		end
	end

	-- islets
	if player.m == 1008 then
		-- muse
		if (player.x == 68 or player.x == 69) and player.y == 7 and (os.time() > player.registry["museBan"] + (60 * 8)) then
			player:warp(3629, 14, 28)
		end
	end

	-- oh mudum crypt for spy
	if player.m == 2205 then
		if player.quest["spy_trials"] == 7 then
			local rand = math.random(1, 100)
			if rand == 1 then
				gravekeeper(player)
			end
		end
	end

	-- vale for spy quest
	if player.m == 1005 then
		if player.quest["spy_trials"] == 13 then
			if (player.x == 245 or player.x == 246 or player.x == 247) and player.y == 179 then
				caravan(player)
			end
		end
	end

	-- stealth grotto spy
	if player.m == 3519 then
		if player.quest["spy_trials"] == 14 then
			if player.x == 12 and player.y == 13 then
				complete_spy(player)
			end
		end
		if player.quest["spy_trials"] == 12 then
			if player.x == 12 and player.y == 13 then
				hwan.interrogate(player)
			end
		end
	end

	-- nagnang
end

complete_spy = async(function(player)
	player.quest["spy_trials"] = 15
	local mistress = {graphic = convertGraphic(5296, "item"), color = 30}
	player:dialogSeq(
		{
			mistress,
			"Ledakannya terdengar sampai sini! Sepertinya kau punya keahlian yang dibutuhkan organisasi kami. Maukah kau bersumpah setia kepada Guild kami?"
		},
		1
	)
	local choices = {
		"Tanda tangani kontraknya dengan namamu.",
		"Tanda tangani kontraknya dengan nama palsu.",
		"Draw a symbol.",
		"Jangan tanda tangani kontraknya dulu."
	}
	local choice = player:menuSeq(
		"Nyonya Guild mengeluarkan kontrak kecil dan menyodorkan pena: ",
		choices,
		{}
	)
	if choice == 1 then
		player:addLegend(
			"Rekan KSG (" .. curT() .. ")",
			"spy_rank",
			22,
			1
		)
		player:dialogSeq(
			{
				mistress,
				"Bagus, mari kuantar masuk ke Balai Guild kami, dan jangan lupa periksa papan Guild..."
			},
			1
		)
	elseif choice == 2 then
		player:dialogSeq(
			{
				mistress,
				"Nama samaran yang menarik... mari kuantar masuk ke balai Guild kami, dan jangan lupa periksa papan Guild..."
			},
			1
		)
	elseif choice == 3 then
		player:dialogSeq(
			{
				mistress,
				"Ya, sudah kuduga kau cerdik... mari kuantar masuk ke balai Guild kami, dan jangan lupa periksa papan Guild..."
			},
			1
		)
	elseif choice == 4 then
		player.quest["spy_trials"] = 14
		player:dialogSeq(
			{mistress, "Tidak masalah. Kembalilah ke sini kapan saja kalau kau ingin bergabung."},
			1
		)
	end
end)

caravan = async(function(player)
	local envoy = {graphic = convertGraphic(7, "monster"), color = 30}
	local explosives = {graphic = convertGraphic(313, "item"), color = 0}
	local spy = {graphic = convertGraphic(102, "monster"), color = 5}
	local item1 = {graphic = convertGraphic(5271, "item"), color = 0}
	local item2 = {graphic = convertGraphic(3745, "item"), color = 0}
	player:dialogSeq(
		{envoy, "** Kau melihat Utusan Kekaisaran menyusuri lorong! **"},
		1
	)
	player:dialogSeq(
		{
			explosives,
			"** Cepat-cepat kau menyamarkan bahan peledak di bawah tumpukan batu, memanjangkan sumbunya, lalu bersembunyi pada jarak aman di bawah jembatan. **"
		},
		1
	)
	player:dialogSeq(
		{
			spy,
			"** Tepat ketika utusan itu mencapai lorong, kau melihat sesosok bayangan turun dari pohon dan mengendap mendekatinya. **"
		},
		1
	)
	player:sendAnimationXY(19, 247, 179, 60)
	player:sendAnimationXY(19, 246, 179, 60)
	player:sendAnimationXY(19, 245, 179, 60)
	player:sendAnimationXY(19, 247, 178, 60)
	player:sendAnimationXY(19, 246, 178, 60)
	player:sendAnimationXY(19, 245, 178, 60)
	player:dialogSeq(
		{
			item1,
			"** Bahan peledak itu meletus di tengah utusan dan sosok bayangan itu, merobohkan dinding sehingga lorongnya tertutup. **"
		},
		1
	)
	player.quest["spy_trials"] = 14
	player.registry["spy_sabotage"] = player.registry["spy_sabotage"] + 1
	player:removeLegendbyName("spy_sabotage")
	player:addLegend(
		"Performed " .. player.registry["spy_sabotage"] .. " tindakan sabotase",
		"spy_sabotage",
		22,
		128
	)
	player:dialogSeq(
		{
			item2,
			"** Kau keluar dari tempat pengintaianmu dan mendapati tidak ada jejak utusan maupun sosok bayangan itu. **",
			"** Hanya gagang belati yang hancur dan potongan seragam Kekaisaran yang hangus berserakan di sana. **"
		},
		1
	)
end)

gravekeeper = async(function(player)
	player:removeItem("handwritten_note", 1)
	local gravekeeper = {graphic = convertGraphic(3490, "item"), color = 0}
	player:dialogSeq(
		{
			gravekeeper,
			"** Kau melihat seorang penjaga makam merawat kuburan yang masih baru. **",
			" ** Ia terpincang mendekat dan memandangimu dari atas ke bawah."
		},
		0
	)
	if player.registry["carnageWin"] < 2 then
		player:dialogSeq(
			{
				gravekeeper,
				"Kau tidak kelihatan seperti orang yang bisa mengirim siapa pun ke liang kubur.",
				"Kembalilah kalau kau sudah punya sedikitnya dua kemenangan riches."
			},
			0
		)
		return
	end
	local response = player:inputSeq(
		"Did you leave something at home for this weather?",
		"",
		"",
		{},
		{}
	)
	if string.lower(response) == "umbrella" then
		player.quest["spy_trials"] = 8
		player:dialogSeq(
			{
				gravekeeper,
				"** Penjaga makam itu berjalan ke pintu ruang kubur, menutupnya dengan bunyi berdebum, lalu mengembalikan suaranya ke nada aslinya. **",
				"Kalau begitu kerja bagus membungkam mulut ember di kasino itu. Guild bilang kau mungkin aset yang berharga bagi jaringan kami... atau jangan-jangan yang satunya yang lewat sini hari ini...",
				"Tidak penting; ada urusan yang harus dibereskan.",
				"Ada orang yang tahu sesuatu yang perlu kami ketahui.",
				"Boleh dibilang ini menyangkut seluruh kerajaan. Bicaralah dengan salah satu ahli intelijen kami, Pond, di perpustakaannya.",
				"Mintalah melihat Koleksi Khusus, dan ia akan memberimu rinciannya."
			},
			0
		)
	else
		player:dialogSeq(
			{gravekeeper, "Yah, kalau begitu aku di sini saja sampai cuacanya berubah."},
			0
		)
	end
end)
