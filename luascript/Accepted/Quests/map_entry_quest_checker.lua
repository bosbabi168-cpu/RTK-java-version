map_entry_quest_checker = async(function(player)
	player.dialogType = 0
	local mapName = player.mapTitle

	if mapName == "Welcome" then
		local t = {graphic = convertGraphic(87, "monster"), color = 7}
		player:dialogSeq(
			{
				t,
				"Halo dan selamat datang di RetroTK. Berjalanlah ke selatan untuk memulai tugas tutorialmu dan memperoleh senjata, zirah, serta mantra pertamamu."
			},
			1
		)
		player:sendMinitext("Daerah ini dirancang untuk membantumu memulai dan memberimu pengetahuan yang kau butuhkan untuk memulai petualanganmu!")
	elseif mapName == "Deep Forest" then
		local t = {graphic = convertGraphic(87, "monster"), color = 7}
		player:dialogSeq(
			{
				t,
				"Kau menyusuri jalan setapak dan bertemu seorang pedagang di padang.\n\nUntuk berbicara dengan pedagang di RetroTK, kau harus mengkliknya dengan penunjuk tetikus.\n\nBicaralah dengan pedagang ini untuk melanjutkan pelajaranmu."
			},
			1
		)
	elseif mapName == "Country Farm" and not player:hasSpell("soothe") then
		if (player.x < 8 and player.y < 5) then
			local t = {graphic = convertGraphic(87, "monster"), color = 7}
			player:dialogSeq(
				{
					t,
					"Selamat!\n\nKau sudah belajar menemukan jalan. Kalau kau melihat kanan bawah layarmu, angkanya sekarang menunjukkan 003 002",
					"Dengan sistem ini dan Peta Kecil (tekan 'm'), kau bisa menemukan jalan di seluruh kota dan desa RetroTK. Tempat-tempat penting juga bisa kau temukan dengan menekan tombol 'F1'"
				},
				1
			)
		end
	elseif mapName == "Angel's Blessing" then
		local npc = NPC("Woodland Angel")
		WoodlandAngelNpc.click(player, npc)
	elseif player.mapTitle == "Kugnae" and player.quest["dagger_blue_rooster"] == 2 and player.quest["crow_took_silvery_acorn"] == 0 then
		if player:hasItem("silvery_acorn", 1) == true then
			player:removeItem("silvery_acorn", 1)
			player.quest["crow_took_silvery_acorn"] = 1
			local t = {graphic = convertGraphic(92, "monster"), color = 0}
			player:freeAsync()
			player:dialogSeq(
				{
					t,
					"Saat kau melangkah ke bawah sinar matahari, kilau acorn itu menarik perhatian seekor gagak. Ia menyambarnya dan terbang ke timur menuju pepohonan beech utara Dae Shore."
				},
				0
			)
		end
	end
end)
